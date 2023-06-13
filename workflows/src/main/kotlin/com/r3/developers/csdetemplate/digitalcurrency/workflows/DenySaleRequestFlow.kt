package com.r3.developers.csdetemplate.digitalcurrency.workflows
import com.r3.developers.csdetemplate.digitalcurrency.contracts.ProductContract
import com.r3.developers.csdetemplate.digitalcurrency.contracts.SaleRequestContract
import com.r3.developers.csdetemplate.digitalcurrency.helpers.findInfo
import com.r3.developers.csdetemplate.digitalcurrency.states.Product
import com.r3.developers.csdetemplate.digitalcurrency.states.SaleRequest
import net.corda.v5.application.flows.*
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

data class DenySaleRequest(
    val productId: UUID,
    val saleRequestId: UUID)

@InitiatingFlow(protocol = "finalize-deny-sale-request-flow")
class DenySaleRequestFlow: AbstractFlow(), ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        logger.info("${this::class.java.enclosingClass}.call() called")
        try {
            val flowArgs = requestBody.getRequestBodyAs(json, DenySaleRequest::class.java)

            val saleRequestStateAndRef = ledgerService.findUnconsumedStatesByType(SaleRequest::class.java)
                .find { it.state.contractState.saleRequestId == flowArgs.saleRequestId }
                ?: throw CordaRuntimeException("Sale Request for id:${flowArgs.saleRequestId} not found")

            val productStateAndRef = ledgerService.findUnconsumedStatesByType(Product::class.java)
                .find { it.state.contractState.productId == flowArgs.productId }
                ?: throw CordaRuntimeException("Product for id:${flowArgs.productId} not found")

            val saleRequestState = saleRequestStateAndRef.state.contractState
            val productState = productStateAndRef.state.contractState

            val notary = notaryLookup.notaryServices.single()

            val updatedProduct = productState.copy(saleRequested = false)

            logger.warn("updated prod: $updatedProduct")
            logger.warn("swer")
            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(saleRequestStateAndRef.ref)
                .addInputState(productStateAndRef.ref)
                .addOutputState(updatedProduct)
                .addCommand(ProductContract.DenyRequestSale())
                .addCommand(SaleRequestContract.Deny())
                .addSignatories(listOf(productState.owner))

            val signedTx = txBuilder.toSignedTransaction()
            val owner = memberLookup.findInfo(productState.owner)
            val session = flowMessaging.initiateFlow(owner.name)

            logger.info("Owner: ${owner.name} denying sale request: ${saleRequestState.saleRequestId} from buyer: ${saleRequestState.buyer}")

            val finalizedTx = ledgerService.finalize(signedTx, listOf(session)).also {
                logger.info("Successfully denied sale request with response $it")
            }

            return finalizedTx.transaction.id.toString()
        } catch (e: Exception) {
            logger.warn("Failed to process deny sale request flow")
            throw e
        }
    }
}

@InitiatedBy(protocol = "finalize-deny-sale-request-flow")
class FinalizeDenySaleRequestResponderFlow: AbstractFlow(), ResponderFlow {

    @Suspendable
    override fun call(session: FlowSession) {
        logger.info("${this::class.java.enclosingClass}.call() called")

        try {
            val finalizedSignedTransaction = ledgerService.receiveFinality(session) { ledgerTransaction ->
                /*val state = ledgerTransaction.getOutputStates(Product::class.java).singleOrNull() ?:
                throw CordaRuntimeException("Failed verification - transaction did not have exactly one output Product.")*/

                logger.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            logger.info("Finished deny sale request responder flow - ${finalizedSignedTransaction.transaction.id}")
        }
        catch (e: Exception) {
            logger.warn("Deny sale request responder flow failed with exception", e)
            throw e
        }
    }
}
