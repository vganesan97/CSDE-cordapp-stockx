package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.DigitalCurrencyContract
import com.r3.developers.csdetemplate.digitalcurrency.helpers.CoinSelection
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

data class TransferDigitalCurrency(val quantity: Int, val toHolder: String)

@InitiatingFlow(protocol = "finalize-transfer-digital-currency-protocol")
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
    lateinit var flowMessaging: FlowMessaging

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

            val availableTokens = ledgerService.findUnconsumedStatesByType(DigitalCurrency::class.java)

            val coinSelection = CoinSelection()
            val (amountSpent, currencyToSpend) = coinSelection.selectTokens(flowArgs.quantity, availableTokens)

            // Send the rest of the other coins to receiver
            // Ignoring opportunity to merge currency
            val spentCurrency = currencyToSpend.map { it.state.contractState.sendTo(toHolder.name) }.toMutableList()

            // Send change back to sender
            if(amountSpent > flowArgs.quantity) {
                val overspend = amountSpent - flowArgs.quantity
                val change = spentCurrency.removeLast() //blindly turn last token into change
                spentCurrency.add(change.sendAmount(overspend)) //change stays with sender
                spentCurrency.add(change.sendAmountTo(change.quantity-overspend, toHolder.name))
            }

            val notary = notaryLookup.notaryServices.single()

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputStates(currencyToSpend.map { it.ref })
                .addOutputStates(spentCurrency)
                .addCommand(DigitalCurrencyContract.Transfer())
                .addSignatories(fromHolder.ledgerKeys.first(), toHolder.ledgerKeys.first()) // issuer does not sign

            val signedTransaction = txBuilder.toSignedTransaction()

            val session = flowMessaging.initiateFlow(toHolder.name)

            val finalizedSignedTransaction = ledgerService.finalize(
                signedTransaction,
                listOf(session)
            )
            return finalizedSignedTransaction.transaction.id.toString().also {
                log.info("Successful ${signedTransaction.commands.first()} with response: $it")
            }
        }
        catch (e: Exception) {
            log.warn("Failed to process transfer digital currency for request body '$requestBody' with exception: '${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "finalize-transfer-digital-currency-protocol")
class FinalizeTransferDigitalCurrencyResponderFlow: ResponderFlow {
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
                val state = ledgerTransaction.getOutputStates(DigitalCurrency::class.java).first() ?:
                    throw CordaRuntimeException("Failed verification - transaction did not have at least one output DigitalCurrency.")

                log.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            log.info("Finished transfer digital currency responder flow - ${finalizedSignedTransaction.transaction.id}")
        }
        catch (e: Exception) {
            log.warn("Transfer DigitalCurrency responder flow failed with exception", e)
            throw e
        }
    }
}

/*
{
    "clientRequestId": "transfer-1",
    "flowClassName": "com.r3.developers.csdetemplate.digitalcurrency.workflows.TransferDigitalCurrencyFlow",
    "requestBody": {
        "quantity":30,
        "toHolder":"CN=Bank of Bob, OU=Test Dept, O=R3, L=NYC, C=US"
    }
}
 */