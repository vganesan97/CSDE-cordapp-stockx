package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.MortgageContract
import com.r3.developers.csdetemplate.digitalcurrency.states.Mortgage
import net.corda.v5.application.flows.*
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

data class IssueMortgage(val address: String, val owner: String, val interestRate: Double)

@InitiatingFlow(protocol = "finalize-issue-mortgage-protocol")
class IssueMortgageFlow: AbstractFlow(), ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("${this::class.java.enclosingClass}.call() called")
        try {
            val flowArgs = requestBody.getRequestBodyAs(json, IssueMortgage::class.java)

            val myInfo = memberLookup.myInfo()
            val owner = memberLookup.lookup(MemberX500Name.parse(flowArgs.owner)) ?:
            throw CordaRuntimeException("MemberLookup can't find owner specified in flow arguments.")

            val mortgage = Mortgage(flowArgs.address,
                Party(owner.name, owner.ledgerKeys.first()),
                flowArgs.interestRate,
                participants = listOf(myInfo.ledgerKeys.first(), owner.ledgerKeys.first()))

            val notary = notaryLookup.notaryServices.single()

            val txBuilder = ledgerService.transactionBuilder
                .setNotary(Party(notary.name, notary.publicKey))
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(mortgage)
                .addCommand(MortgageContract.Issue())
                .addSignatories(mortgage.participants)

            val signedTransaction = txBuilder.toSignedTransaction()

            val session = flowMessaging.initiateFlow(owner.name)

            val finalizedSignedTransaction = ledgerService.finalize(
                signedTransaction,
                listOf(session)
            )
            return finalizedSignedTransaction.id.toString().also {
                log.info("Successful ${signedTransaction.commands.first()} with response: $it")
            }
        }
        catch (e: Exception) {
            log.warn("Failed to process issue mortgage for request body '$requestBody' with exception: '${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "finalize-issue-mortgage-protocol")
class FinalizeIssueMortgageResponderFlow: ResponderFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("${this::class.java.enclosingClass}.call() called")

        try {
            val finalizedSignedTransaction = ledgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.getOutputStates(Mortgage::class.java).singleOrNull() ?:
                throw CordaRuntimeException("Failed verification - transaction did not have exactly one output Mortgage.")

                log.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            log.info("Finished issue mortgage responder flow - ${finalizedSignedTransaction.id}")
        }
        catch (e: Exception) {
            log.warn("Issue Mortgage responder flow failed with exception", e)
            throw e
        }
    }
}