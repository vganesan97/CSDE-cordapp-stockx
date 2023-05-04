package com.r3.developers.csdetemplate.digitalcurrency.workflows;

import net.corda.v5.application.flows.CordaInject;
import net.corda.v5.application.flows.FlowEngine;
import net.corda.v5.application.marshalling.JsonMarshallingService;
import net.corda.v5.application.membership.MemberLookup;
import net.corda.v5.application.messaging.FlowMessaging;
import net.corda.v5.ledger.common.NotaryLookup;
import net.corda.v5.ledger.utxo.UtxoLedgerService;
import org.slf4j.LoggerFactory

abstract class AbstractFlow {
    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var json: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }


}
