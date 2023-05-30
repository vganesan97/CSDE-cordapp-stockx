package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.SaleRequestContract
import com.r3.developers.csdetemplate.digitalcurrency.helpers.findInfo
import com.r3.developers.csdetemplate.digitalcurrency.states.SaleRequest
import net.corda.v5.application.flows.*
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.util.UUID

data class ApproveSaleRequest(val productId: UUID)

@InitiatingFlow(protocol = "approve-sale-request-protocol")
class ApproveSaleRequestFlow: AbstractFlow(), ClientStartableFlow {

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        logger.info("${this::class.java.enclosingClass}.call() called")
        try {
            val flowArgs = requestBody.getRequestBodyAs(json, ApproveSaleRequest::class.java)
            val approver = memberLookup.myInfo()

            // Find the SaleRequest by saleRequestId
            val saleRequestStateAndRef = ledgerService.findUnconsumedStatesByType(SaleRequest::class.java)
                .find { it.state.contractState.productId == flowArgs.productId }
                ?: throw CordaRuntimeException("SaleRequest not found")

            val saleRequest = saleRequestStateAndRef.state.contractState

            // Check that the approver is the seller in the saleRequest
            if (approver.ledgerKeys.first() != saleRequest.owner) {
                throw CordaRuntimeException("Only the seller can approve a sale request.")
            }

            // Get a reference to the notary service on the network
            val notary = notaryLookup.notaryServices.single()

            // Build the transaction
            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .addInputState(saleRequestStateAndRef.ref)
                .addCommand(SaleRequestContract.Accept())
                .addSignatories(listOf(approver.ledgerKeys.first()))

            // Sign and finalize the transaction
            val signedTransaction = txBuilder.toSignedTransaction()
            val sessions = saleRequest.participants.map { memberLookup.findInfo(it) }.map { flowMessaging.initiateFlow(it.name) }
            val finalizedSignedTransaction = ledgerService.finalize(
                signedTransaction,
                sessions
            )

            // After finalizing the transaction which approves the sale request,
            // run the SellProductFlow to handle the product sale and token transfers
            // Assuming SellProductFlow accepts the product id as an argument

            flowEngine.subFlow(SellProductSubFlow(
                saleRequest.productId,
                saleRequest.buyer,
                saleRequest.price
            ))

            return finalizedSignedTransaction.transaction.id.toString().also {
                logger.info("Successful SaleRequest with response: $it")
            }
        } catch (e: Exception) {
            logger.warn("Failed to process sale request for request body '$requestBody' with exception: '${e.message}'")
            throw e
        }
    }
}


@InitiatedBy(protocol = "approve-sale-request-protocol")
class ApproveSaleRequestResponderFlow: AbstractFlow(), ResponderFlow {

    @Suspendable
    override fun call(session: FlowSession) {
        logger.info("${this::class.java.enclosingClass}.call() called")

        try {
            logger.info("Buyer waiting to receive approval for sale request")
            val finalizedSignedTransaction = ledgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.getOutputStates(SaleRequest::class.java).first()
                    ?: throw CordaRuntimeException("Failed verification - transaction did not have at least one output SaleRequest.")

                logger.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            val outputSaleRequest = finalizedSignedTransaction.transaction.outputStateAndRefs.filterIsInstance<SaleRequest>().first()
            logger.info("Buyer received approval for sale request with productId ${outputSaleRequest.productId}")
            logger.info("Transaction id for the approval ${finalizedSignedTransaction.transaction.id}")
        } catch (e: Exception) {
            logger.warn("Approve Sale Request responder flow failed with exception", e)
            throw e
        }
    }
}
