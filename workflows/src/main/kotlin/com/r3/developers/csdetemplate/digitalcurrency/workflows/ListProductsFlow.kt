package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.ProductContract
import com.r3.developers.csdetemplate.digitalcurrency.helpers.findInfo
import com.r3.developers.csdetemplate.digitalcurrency.states.DigitalCurrency
import com.r3.developers.csdetemplate.digitalcurrency.states.Mortgage
import com.r3.developers.csdetemplate.digitalcurrency.states.Product
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

data class ProductsStateResults(val productId: UUID,
                                val condition: String,
                                val listingDetails: String,
                                val owner: MemberX500Name,
                                val price: Double,
                                val forAuction: Boolean,
                                val saleRequested: Boolean,
                                val name: String)
class ListProductsFlow: ClientStartableFlow {

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
        log.warn("ListProductsFlow.call() called")

        val queryingMember = memberLookup.myInfo()
        val states1 = ledgerService.findUnconsumedStatesByType(Product::class.java)
            .filter { it.state.contractState.owner == queryingMember.ledgerKeys.first() }


        log.warn("all member info: ${memberLookup.lookup().map{it.name.toString()}}")
        val states = ledgerService.findUnconsumedStatesByType(Product::class.java)


        val results = states.map {
            ProductsStateResults(
                it.state.contractState.productId,
                it.state.contractState.condition,
                it.state.contractState.listingDetails,
                memberLookup.findInfo(it.state.contractState.owner).name,
                it.state.contractState.price,
                it.state.contractState.forAuction,
                it.state.contractState.saleRequested,
                it.state.contractState.name
            )
        }
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