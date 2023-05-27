package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.states.Product
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey
import java.util.UUID

data class CreateProduct(val owner: String,
                         val name: String,
                         val condition: String,
                         val price: Double,
                         val listingDetails: String)

@InitiatingFlow(protocol = "finalize-create-product-flow")
class CreateProductFlow(): AbstractFlow(), ClientStartableFlow {

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        logger.info("${this::class.java.enclosingClass}.call() called")
        try {
            val flowArgs = requestBody.getRequestBodyAs(json, CreateProduct::class.java)

            val myInfo = memberLookup.myInfo()
            val owner =  memberLookup.lookup(MemberX500Name.parse(flowArgs.owner)) ?:
                throw CordaRuntimeException("MemberLookup can't find owner specified in flow arguments.")

            val product = Product(owner.ledgerKeys.first(),
                productId = UUID.randomUUID(),
                flowArgs.name,
                flowArgs.listingDetails,
                flowArgs.condition,
                flowArgs.price,
                participants = listOf(myInfo.ledgerKeys.first(), owner.ledgerKeys.first()))

            val notary = notaryLookup.notaryServices.single()
            val signatories = mutableListOf<PublicKey>(myInfo.ledgerKeys.first()).union(product.participants)
//            val txBuilder = ledgerService.createTransactionBuilder()
//                .setNotary(notary.name)
//                .set
            return ""
        }
        catch (e: Exception) {
            logger.warn("create product failed")
            throw e
        }
    }
}