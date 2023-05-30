package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.states.Product
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import java.util.*


data class SellProduct(val productId: UUID, val buyer: String)

@InitiatingFlow(protocol = "finalize-sell-product-flow")
class SellProductFlow: AbstractFlow(), ClientStartableFlow {
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        logger.info("${this::class.java.enclosingClass}.call() called")
        try {
            val flowArgs = requestBody.getRequestBodyAs(json, SellProduct::class.java)

            val buyer = memberLookup.lookup(MemberX500Name.parse(flowArgs.buyer)) ?:
                throw CordaRuntimeException("MemberLookup can't find toHolder specified in flow arguments.")

            val existingProduct = ledgerService.findUnconsumedStatesByType(Product::class.java)

            val productToSell = existingProduct.singleOrNull { product ->
                flowArgs.productId == product.state.contractState.productId
            } ?: throw CordaRuntimeException("Product with the provided ID not found.")

            val price = productToSell.state.contractState.price

            return flowEngine.subFlow(SellProductSubFlow(
                flowArgs.productId,
                buyer.ledgerKeys.first(),
                price
            ))

        } catch (e: Exception) {
            logger.warn("Failed to process sell product flow")
            throw e
        }
    }

}