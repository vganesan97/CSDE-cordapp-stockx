package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.DigitalCurrencyContract
import com.r3.developers.csdetemplate.digitalcurrency.helpers.CoinSelection
import com.r3.developers.csdetemplate.digitalcurrency.states.DigitalCurrency
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

data class WithdrawDigitalCurrency(val quantity: Int)

@InitiatingFlow(protocol = "finalize-withdraw-digital-currency-protocol")
class WithdrawDigitalCurrencyFlow: ClientStartableFlow {
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

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("${this::class.java.enclosingClass}.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, WithdrawDigitalCurrency::class.java)

            if(flowArgs.quantity <= 0) {
                throw CordaRuntimeException("Must withdrawl a positive amount of currency.")
            }

            val fromHolder = memberLookup.myInfo()

            val availableTokens = ledgerService.findUnconsumedStatesByType(DigitalCurrency::class.java)

            val coinSelection = CoinSelection()
            val (amountSpent, currencyToWithdraw) = coinSelection.selectTokens(flowArgs.quantity, availableTokens)

            val fromParty = Party(fromHolder.name, fromHolder.ledgerKeys.first())

            // Send change back to sender
            val change = if (amountSpent > flowArgs.quantity) {
                val overspend = amountSpent - flowArgs.quantity
                val lastDigitalCurrency = currencyToWithdraw.last() //blindly turn last token into change
                lastDigitalCurrency.state.contractState.sendAmountTo(overspend, fromParty) //change stays with sender
            } else {
                null
            }

            val notary = notaryLookup.notaryServices.single()

            val txBuilder = ledgerService.transactionBuilder
                .setNotary(Party(notary.name, notary.publicKey))
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputStates(currencyToWithdraw.map { it.ref })
                .addOutputStates(change)
                .addCommand(DigitalCurrencyContract.Withdraw())
                .addSignatories(fromParty.owningKey) // issuer does not sign

            val signedTransaction = txBuilder.toSignedTransaction()

            val finalizedSignedTransaction = ledgerService.finalize(
                signedTransaction,
                listOf()
            )

            return finalizedSignedTransaction.id.toString().also {
                log.info("Successful ${signedTransaction.commands.first()} with response: $it")
            }
        } catch (e: Exception) {
            log.warn("Failed to process transfer digital currency for request body '$requestBody' with exception: '${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "finalize-withdraw-digital-currency-protocol")
class FinalizeWithdrawDigitalCurrencyResponderFlow: ResponderFlow {
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
                log.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            log.info("Finished transfer digital currency responder flow - ${finalizedSignedTransaction.id}")
        }
        catch (e: Exception) {
            log.warn("Transfer DigitalCurrency responder flow failed with exception", e)
            throw e
        }
    }
}

/*
{
    "clientRequestId": "withdrawal-1",
    "flowClassName": "com.r3.developers.csdetemplate.digitalcurrency.workflows.WithdrawDigitalCurrencyFlow",
    "requestBody": {
        "quantity":30
    }
}
 */