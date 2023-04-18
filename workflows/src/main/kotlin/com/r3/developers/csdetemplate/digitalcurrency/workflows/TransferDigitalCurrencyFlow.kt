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
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

data class TransferDigitalCurrency(val quantity: Int, val toHolder: String)

class TransferDigitalCurrencyFlow: ClientStartableFlow {
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
        log.info("${this::class.java.enclosingClass}.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, TransferDigitalCurrency::class.java)

            val fromHolder = memberLookup.myInfo()

            if (flowArgs.toHolder == fromHolder.name.toString()) {
                throw CordaRuntimeException("Cannot transfer money to self.")
            }

            val toHolder = memberLookup.lookup(MemberX500Name.parse(flowArgs.toHolder)) ?:
                throw CordaRuntimeException("MemberLookup can't find toHolder specified in flow arguments.")

            val availableCurrency = ledgerService.findUnconsumedStatesByType(DigitalCurrency::class.java)

            // Simple (unoptimized) coin selection for learning purposes only
            val currencyToSpend = mutableListOf<StateAndRef<DigitalCurrency>>()
            var amountSpent = 0
            for (currency in availableCurrency) {
                currencyToSpend += currency
                amountSpent += currency.state.contractState.quantity
                if (amountSpent > flowArgs.quantity) {
                    break
                }
            }

            if (amountSpent < flowArgs.quantity) {
                throw CordaRuntimeException("Insufficient Funds.")
            }

            // Send the rest of the other coins to receiver
            // Ignoring opportunity to merge currency
            val fromParty = Party(fromHolder.name, fromHolder.ledgerKeys.first())
            val toParty = Party(toHolder.name, toHolder.ledgerKeys.first())
            val spentCurrency = currencyToSpend.map { it.state.contractState.sendTo(toParty) }.toMutableList()

            // Send change back to sender
            if(amountSpent > flowArgs.quantity) {
                val overspend = amountSpent - flowArgs.quantity
                val change = spentCurrency.removeLast() //blindly turn last token into change
                spentCurrency.add(change.sendAmountTo(overspend, fromParty)) //change stays with sender
                spentCurrency.add(change.sendAmountTo(change.quantity-overspend, toParty))
            }

            val notary = notaryLookup.notaryServices.single()

            val txBuilder = ledgerService.transactionBuilder
                .setNotary(Party(notary.name, notary.publicKey))
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputStates(currencyToSpend.map { it.ref })
                .addOutputStates(spentCurrency)
                .addCommand(DigitalCurrencyContract.Transfer())
                .addSignatories(fromParty.owningKey, toParty.owningKey) // issuer does not sign

            val signedTransaction = txBuilder.toSignedTransaction()

            return flowEngine.subFlow(FinalizeDigitalCurrencySubFlow(signedTransaction, toHolder.name))
        }
        catch (e: Exception) {
            log.warn("Failed to process transfer digital currency for request body '$requestBody' with exception: '${e.message}'")
            throw e
        }
    }

}