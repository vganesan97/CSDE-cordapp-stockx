package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.DigitalCurrencyContract
import com.r3.developers.csdetemplate.digitalcurrency.states.DigitalCurrency
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

data class IssueDigitalCurrency(val quantity: Int, val holder: String)

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
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        log.info("IssueDigitalCurrency.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, IssueDigitalCurrency::class.java)

            val myInfo = memberLookup.myInfo()
            val holder = memberLookup.lookup(MemberX500Name.parse(flowArgs.holder)) ?:
                throw CordaRuntimeException("MemberLookup can't find holder specified in flow arguments.")

            val digitalCurrency = DigitalCurrency(flowArgs.quantity,
                Party(holder.name, holder.ledgerKeys.first()),
                participants = listOf(myInfo.ledgerKeys.first(), holder.ledgerKeys.first()))

            val notary = notaryLookup.notaryServices.single()

            val txBuilder = ledgerService.getTransactionBuilder()
                .setNotary(Party(notary.name, notary.publicKey))
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(digitalCurrency)
                .addCommand(DigitalCurrencyContract.Issue())
                .addSignatories(digitalCurrency.participants)

            val signedTransaction = txBuilder.toSignedTransaction()

            return flowEngine.subFlow(FinalizeDigitalCurrencySubFlow(signedTransaction, holder.name))

        }
        catch (e: Exception) {
            log.warn("Failed to process issue digital currency for request body '$requestBody' with exception: '${e.message}'")
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