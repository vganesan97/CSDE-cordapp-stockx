package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.ProductContract
import com.r3.developers.csdetemplate.digitalcurrency.states.Product
import com.r3.developers.csdetemplate.digitalcurrency.contracts.SaleRequestContract
import com.r3.developers.csdetemplate.digitalcurrency.helpers.findInfo
import com.r3.developers.csdetemplate.digitalcurrency.states.SaleRequest
import net.corda.v5.application.flows.*
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class BuyProduct(val productId: UUID)
@InitiatingFlow(protocol = "create-sale-request-protocol")
class BuyProductFlow: AbstractFlow(), ClientStartableFlow {

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        logger.info("${this::class.java.enclosingClass}.call() called")
        try {
            val flowArgs = requestBody.getRequestBodyAs(json, BuyProduct::class.java)

            val buyer = memberLookup.myInfo()

            // Find the product by productId
            val productStateAndRef = ledgerService.findUnconsumedStatesByType(Product::class.java)
                .find { it.state.contractState.productId == flowArgs.productId }
                ?: throw CordaRuntimeException("Product not found")

            val product = productStateAndRef.state.contractState
            val updatedProduct = product.copy(saleRequested = true)

            val saleRequest = SaleRequest(
                saleRequestId = UUID.randomUUID(),
                product.productId,
                product.price,
                buyer.ledgerKeys.first(),
                product.owner,
                participants = listOf(product.owner)
            )

            val notary = notaryLookup.notaryServices.single()

            val signatories = mutableListOf<PublicKey>(buyer.ledgerKeys.first())
            signatories.union(saleRequest.participants)

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(productStateAndRef.ref)
                .addOutputState(saleRequest)
                .addOutputState(updatedProduct)
                .addCommand(SaleRequestContract.Create())
                .addCommand(ProductContract.AcceptRequestSale())
                .addSignatories(signatories)

            val owner = memberLookup.findInfo(product.owner)
            val session = flowMessaging.initiateFlow(owner.name)
            val signedTransaction = txBuilder.toSignedTransaction()

            logger.info("Buyer sending sale request to ${session.counterparty}")

            val finalizedSignedTransaction = ledgerService.finalize(
                signedTransaction,
                listOf(session)
            )

            logger.info("Buyer sale request sent to ${session.counterparty}")

            return finalizedSignedTransaction.transaction.id.toString().also {
                logger.info("Successful SaleRequest with response: $it")
            }
        }
        catch (e: Exception) {
            logger.warn("Failed to process sale request for request body '$requestBody' with exception: '${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "create-sale-request-protocol")
class BuyProductResponderFlow: AbstractFlow(), ResponderFlow {

    @Suspendable
    override fun call(session: FlowSession) {
        logger.info("${this::class.java.enclosingClass}.call() called")

        try {
            logger.info("Seller waiting to receive sale request")
            val finalizedSignedTransaction = ledgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.getOutputStates(SaleRequest::class.java).first()
                    ?: throw CordaRuntimeException("Failed verification - transaction did not have at least one output SaleRequest.")

                logger.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            //val outputSaleRequest = finalizedSignedTransaction.transaction.outputStateAndRefs.filterIsInstance<SaleRequest>().first()
           // logger.info("Seller received sale request for product id ${outputSaleRequest.productId}")
            logger.info("Seller received sale request for product id ${finalizedSignedTransaction.transaction.id}")
        } catch (e: Exception) {
            logger.warn("Buy Product responder flow failed with exception", e)
            throw e
        }
    }
}