package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.helpers.findInfo
import com.r3.developers.csdetemplate.digitalcurrency.states.SaleRequest
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.util.UUID

data class SaleRequestResults(
    val saleRequestId: UUID,
    val productId: UUID,
    val price: Double,
    val buyer: MemberX500Name,
    val owner: MemberX500Name,
    val accepted: Boolean)

class ListSaleRequestsFlow: ClientStartableFlow {

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
        log.info("ListSaleRequestsFlow.call() called")
        val queryingMember = memberLookup.myInfo()

        val states = ledgerService.findUnconsumedStatesByType(SaleRequest::class.java).filter { saleRequest ->
            saleRequest.state.contractState.owner == queryingMember.ledgerKeys.first()
        }

        val results = states.map {
            SaleRequestResults(
                it.state.contractState.saleRequestId,
                it.state.contractState.productId,
                it.state.contractState.price,
                memberLookup.findInfo(it.state.contractState.buyer).name,
                memberLookup.findInfo(it.state.contractState.owner).name,
                it.state.contractState.accepted)
        }

        return jsonMarshallingService.format(results)
    }

}