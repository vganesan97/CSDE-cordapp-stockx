package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.ProductContract
import com.r3.developers.csdetemplate.digitalcurrency.helpers.findInfo
import com.r3.developers.csdetemplate.digitalcurrency.states.Product
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class CreateAuction(val productId: UUID)

@InitiatingFlow(protocol = "start-auction-protocol")
class CreateAuctionFlow: AbstractFlow(), ClientStartableFlow {

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        logger.info("${this::class.java.enclosingClass}.call() called")
        try {
            val myInfo = memberLookup.myInfo()
            val flowArgs = requestBody.getRequestBodyAs(json, CreateAuction::class.java)

            val productStateAndRef = ledgerService.findUnconsumedStatesByType(Product::class.java)
                .find { it.state.contractState.owner == myInfo.ledgerKeys.first() &&
                        it.state.contractState.productId == flowArgs.productId }
                ?: throw CordaRuntimeException("Product for ${flowArgs.productId} not found.")

            val product = productStateAndRef.state.contractState
            val notary = notaryLookup.notaryServices.single()

            // Build the transaction
            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(productStateAndRef.ref)
                .addCommand(ProductContract.Auction())
                .addOutputState(product.copy(forAuction = true))
                .addSignatories(listOf(myInfo.ledgerKeys.first()))

            // If the subflow succeeded, finalize the transaction
            val sessions = product.participants
                .map { memberLookup.findInfo(it) }
                .map { flowMessaging.initiateFlow(it.name) }

            val signedTransaction = txBuilder.toSignedTransaction()
            val finalizedSignedTransaction = ledgerService.finalize(signedTransaction, sessions)

            return finalizedSignedTransaction.transaction.id.toString().also {
                logger.info("Successful ${signedTransaction.commands.first()} with response: $it")
            }

        } catch (e: Exception) {
            logger.warn("Failed create auction for request body '$requestBody' with exception: '${e.message}'")
            throw e
        }
    }

}