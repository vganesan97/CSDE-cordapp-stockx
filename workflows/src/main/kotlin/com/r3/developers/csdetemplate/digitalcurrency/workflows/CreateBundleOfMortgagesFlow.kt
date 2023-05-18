package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.BundleOfMortgagesContract
import com.r3.developers.csdetemplate.digitalcurrency.states.BundleOfMortgages
import net.corda.v5.application.flows.*
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.time.Duration
import java.time.Instant
import java.util.*

data class CreateBundleOfMortgages(val mortgageIds: List<UUID>)

@InitiatingFlow(protocol = "finalize-create-bundle-protocol")
class CreateBundleOfMortgagesFlow: AbstractFlow(), ClientStartableFlow {

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        logger.info("${this::class.java.enclosingClass}.call() called")
        try {
            val flowArgs = requestBody.getRequestBodyAs(json, CreateBundleOfMortgages::class.java)

            val myInfo = memberLookup.myInfo()

            val bundle = BundleOfMortgages(
                bundleId = UUID.randomUUID(),
                myInfo.ledgerKeys.first(),
                flowArgs.mortgageIds,
                participants = listOf(myInfo.ledgerKeys.first()))

            val notary = notaryLookup.notaryServices.single()

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(bundle)
                .addCommand(BundleOfMortgagesContract.Create())
                .addSignatories(myInfo.ledgerKeys.first())

            val selfSignedTransaction = txBuilder.toSignedTransaction()

            val session = flowMessaging.initiateFlow(myInfo.name)

            val finalizedSignedTransaction = ledgerService.finalize(
                selfSignedTransaction, listOf(session))

            return finalizedSignedTransaction.transaction.id.toString().also {
                logger.info("Successful ${selfSignedTransaction.commands.first()} with response: $it")
            }
        }
        catch (e: Exception) {
            logger.warn("Failed to process bundle mortgage for request body '$requestBody' with exception: '${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "finalize-create-bundle-protocol")
class FinalizeCreateBundleOfMortgagesResponderFlow: AbstractFlow(), ResponderFlow {

    @Suspendable
    override fun call(session: FlowSession) {
        logger.info("${this::class.java.enclosingClass}.call() called")

        try {
            val finalizedSignedTransaction = ledgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.getOutputStates(BundleOfMortgages::class.java).singleOrNull() ?:
                throw CordaRuntimeException("Failed verification - transaction did not have exactly one output BundleOfMortgages.")

                logger.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            logger.info("Finished create bundle of mortgages responder flow - ${finalizedSignedTransaction.transaction.id}")
        }
        catch (e: Exception) {
            logger.warn("Create Bundle of Mortgages responder flow failed with exception", e)
            throw e
        }
    }
}