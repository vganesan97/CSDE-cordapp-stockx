package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.DigitalCurrencyContract
import com.r3.developers.csdetemplate.digitalcurrency.states.DigitalCurrency
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

data class IssueDigitalCurrency(val quantity: Int, val holder: String)

@InitiatingFlow(protocol = "finalize-issue-digital-currency-protocol")
class IssueDigitalCurrencyFlow: ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        log.info("${this::class.java.enclosingClass}.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, IssueDigitalCurrency::class.java)

            val myInfo = memberLookup.myInfo()
            val holder = memberLookup.lookup(MemberX500Name.parse(flowArgs.holder)) ?:
                throw CordaRuntimeException("MemberLookup can't find holder specified in flow arguments.")

            val digitalCurrency = DigitalCurrency(flowArgs.quantity,
                holder.name,
                participants = listOf(myInfo.ledgerKeys.first(), holder.ledgerKeys.first()))

            val notary = notaryLookup.notaryServices.single()

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(digitalCurrency)
                .addCommand(DigitalCurrencyContract.Issue())
                .addSignatories(digitalCurrency.participants)

            val signedTransaction = txBuilder.toSignedTransaction()

            val session = flowMessaging.initiateFlow(holder.name)

            val finalizedSignedTransaction = ledgerService.finalize(
                signedTransaction,
                listOf(session)
            )
            return finalizedSignedTransaction.transaction.id.toString().also {
                log.info("Successful ${signedTransaction.commands.first()} with response: $it")
            }
        }
        catch (e: Exception) {
            log.warn("Failed to process issue digital currency for request body '$requestBody' with exception: '${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "finalize-issue-digital-currency-protocol")
class FinalizeIssueDigitalCurrencyResponderFlow: ResponderFlow {
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
                val state = ledgerTransaction.getOutputStates(DigitalCurrency::class.java).singleOrNull() ?:
                throw CordaRuntimeException("Failed verification - transaction did not have exactly one output DigitalCurrency.")

                log.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            log.info("Finished issue digital currency responder flow - ${finalizedSignedTransaction.transaction.id}")
        }
        catch (e: Exception) {
            log.warn("Issue DigitalCurrency responder flow failed with exception", e)
            throw e
        }
    }
}

/*
RequestBody for triggering the flow via REST:
{
    "clientRequestId": "issue-1",
    "flowClassName": "com.r3.developers.csdetemplate.digitalcurrency.workflows.IssueDigitalCurrencyFlow",
    "requestBody": {
        "quantity":100,
        "holder":"CN=Bank of Alice, OU=Test Dept, O=R3, L=NYC, C=US",
    }
}
 */