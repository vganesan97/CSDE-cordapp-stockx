package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.states.DigitalCurrency
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory

data class DigitalCurrencyStateResults(val quantity: Int, val holder: MemberX500Name)

class ListDigitalCurrencyFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("ListChatsFlow.call() called")

        val states = ledgerService.findUnconsumedStatesByType(DigitalCurrency::class.java)
        val results = states.map {
            DigitalCurrencyStateResults(
                it.state.contractState.quantity,
                it.state.contractState.holder) }

        return jsonMarshallingService.format(results)
    }
}

/*
RequestBody for triggering the flow via REST:
{
    "clientRequestId": "list-1",
    "flowClassName": "com.r3.developers.csdetemplate.digitalcurrency.workflows.ListChatsFlow",
    "requestBody": {}
}
*/