package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.helpers.findInfo
import com.r3.developers.csdetemplate.digitalcurrency.states.BundleOfMortgages
import com.r3.developers.csdetemplate.digitalcurrency.states.Mortgage
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.util.*

data class ListMortgagesByBundleId(val bundleId: UUID)

class ListMortgagesByBundleIdFlow : AbstractFlow(), ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("ListMortgagesByBundleIdFlow.call() called")
        val flowArgs = requestBody.getRequestBodyAs(json, ListMortgagesByBundleId::class.java)

        val queryingMember = memberLookup.myInfo()

        val bundle = ledgerService.findUnconsumedStatesByType(BundleOfMortgages::class.java).filter { bundle ->
            bundle.state.contractState.bundleId == flowArgs.bundleId
        }

        if( bundle.isEmpty()) throw CordaRuntimeException("No bundle found for id: ${flowArgs.bundleId}")

        val mortgages = ledgerService.findUnconsumedStatesByType(Mortgage::class.java).filter { mortgage ->
            mortgage.state.contractState.owner == queryingMember.ledgerKeys.first() &&
                    bundle.first().state.contractState.mortgageIds.contains(mortgage.state.contractState.mortgageId)
        }

        val results = mortgages.map {
            MortgagesStateResults(
                it.state.contractState.address,
                it.state.contractState.mortgageId,
                memberLookup.findInfo(it.state.contractState.owner).name,
                it.state.contractState.interestRate,
                it.state.contractState.fixedInterestRate,
                it.state.contractState.loanToValue,
                it.state.contractState.condition,
                it.state.contractState.creditQualityRating,
                it.state.contractState.listingDetails,
                it.state.contractState.bundled) }

        return json.format(results)
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