package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.helpers.findInfo
import com.r3.developers.csdetemplate.digitalcurrency.states.Mortgage
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.util.*

data class MortgagesStateResults(val address: String, val mortgageId: UUID, val owner: MemberX500Name, val interestRate: Double, val fixedIR: Boolean, val loanToValue: Double, val condition: String, val creditQualityRating: String, val listingDetails: Boolean)

class ListMortgagesFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("ListMortgagesFlow.call() called")

        val states = ledgerService.findUnconsumedStatesByType(Mortgage::class.java)
        val results = states.map {
            MortgagesStateResults(
                it.state.contractState.address,
                it.state.contractState.mortgageId,
                memberLookup.findInfo(it.state.contractState.owner).name,
                it.state.contractState.interestRate,
                it.state.contractState.fixedIR,
                it.state.contractState.loanToValue,
                it.state.contractState.condition,
                it.state.contractState.creditQualityRating,
                it.state.contractState.listingDetails) }

        return jsonMarshallingService.format(results)
    }
}

/*
RequestBody for triggering the flow via REST:
{
    "clientRequestId": "list-mortgages-1",
    "flowClassName": "com.r3.developers.csdetemplate.digitalcurrency.workflows.ListMortgagesFlow",
    "requestBody": {}
}
*/