package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.MortgageContract
import com.r3.developers.csdetemplate.digitalcurrency.states.Mortgage
import net.corda.v5.application.flows.*
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import java.time.Duration
import java.time.Instant
import java.util.*

data class IssueMortgage(val address: String,
                         val owner: String,
                         val interestRate: Double,
                         val fixedInterestRate: Boolean,
                         val loanToValue: Double,
                         val condition: String,
                         val creditQualityRating: String,
                         val listingDetails: String)

@InitiatingFlow(protocol = "finalize-issue-mortgage-protocol")
class IssueMortgageFlow: AbstractFlow(), ClientStartableFlow {

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        logger.info("${this::class.java.enclosingClass}.call() called")
        try {
            val flowArgs = requestBody.getRequestBodyAs(json, IssueMortgage::class.java)

            val myInfo = memberLookup.myInfo()
            val owner = memberLookup.lookup(MemberX500Name.parse(flowArgs.owner)) ?:
                throw CordaRuntimeException("MemberLookup can't find owner specified in flow arguments.")

            val mortgage = Mortgage(flowArgs.address,
                mortgageId = UUID.randomUUID(),
                owner.ledgerKeys.first(),
                flowArgs.interestRate,
                flowArgs.fixedInterestRate,
                flowArgs.loanToValue,
                flowArgs.condition,
                flowArgs.creditQualityRating,
                flowArgs.listingDetails,

                participants = listOf(myInfo.ledgerKeys.first(), owner.ledgerKeys.first()))

            val notary = notaryLookup.notaryServices.single()

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(mortgage)
                .addCommand(MortgageContract.Issue())
                .addSignatories(mortgage.participants)
                .addSignatories(myInfo.ledgerKeys.first())

            val signedTransaction = txBuilder.toSignedTransaction()

            val session = flowMessaging.initiateFlow(owner.name)

            val finalizedSignedTransaction = ledgerService.finalize(
                signedTransaction,
                listOf(session)
            )
            return finalizedSignedTransaction.transaction.id.toString().also {
                logger.info("Successful ${signedTransaction.commands.first()} with response: $it")
            }
        }
        catch (e: Exception) {
            logger.warn("Failed to process issue mortgage for request body '$requestBody' with exception: '${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "finalize-issue-mortgage-protocol")
class FinalizeIssueMortgageResponderFlow: AbstractFlow(), ResponderFlow {

    @Suspendable
    override fun call(session: FlowSession) {
        logger.info("${this::class.java.enclosingClass}.call() called")

        try {
            val finalizedSignedTransaction = ledgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.getOutputStates(Mortgage::class.java).singleOrNull() ?:
                throw CordaRuntimeException("Failed verification - transaction did not have exactly one output Mortgage.")

                logger.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            logger.info("Finished issue mortgage responder flow - ${finalizedSignedTransaction.transaction.id}")
        }
        catch (e: Exception) {
            logger.warn("Issue Mortgage responder flow failed with exception", e)
            throw e
        }
    }
}