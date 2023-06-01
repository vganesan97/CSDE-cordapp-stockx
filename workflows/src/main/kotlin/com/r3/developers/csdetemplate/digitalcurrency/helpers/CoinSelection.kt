package com.r3.developers.csdetemplate.digitalcurrency.helpers

import com.r3.developers.csdetemplate.digitalcurrency.states.DigitalCurrency
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.StateAndRef
import java.security.PublicKey

class CoinSelection @JvmOverloads constructor() {

    fun selectTokensForTransfer(quantity: Double,
                                sender: PublicKey,
                                recipient: PublicKey,
                                availableTokens: List<StateAndRef<DigitalCurrency>>):
            Pair<List<StateAndRef<DigitalCurrency>>, List<DigitalCurrency>> {
         val (amountSpent, selectedTokens) = selectTokens(quantity, availableTokens)
        val spentCurrency = selectedTokens.map {
            it.state.contractState.sendTo(recipient)
        }.toMutableList()

        if(amountSpent > quantity) {
            val overspend = amountSpent - quantity
            val change = spentCurrency.removeLast()
            spentCurrency.add(change.sendAmountTo(overspend, sender))
            spentCurrency.add(change.sendAmount(change.quantity-overspend))
        }

        return Pair(selectedTokens, spentCurrency)
    }

    fun selectTokensForRedemption(quantity: Double,
                                  availableTokens: List<StateAndRef<DigitalCurrency>>):
            Pair<List<StateAndRef<DigitalCurrency>>, DigitalCurrency?> {
        val (amountSpent, currencyToWithdraw) = selectTokens(quantity, availableTokens)

        val remainingCurrency = if (amountSpent > quantity) {
            val change = amountSpent - quantity
            val lastDigitalCurrency = currencyToWithdraw.last()
            lastDigitalCurrency.state.contractState.sendAmount(change)
        } else {
            null
        }

        return Pair(currencyToWithdraw, remainingCurrency)
    }

    private fun selectTokens(quantity: Double,
                             availableTokens: List<StateAndRef<DigitalCurrency>>)
            : Pair<Double, List<StateAndRef<DigitalCurrency>>> {
        val selectedTokens = mutableListOf<StateAndRef<DigitalCurrency>>()
        var amountSpent = 0.0
        for (token in availableTokens) {
            selectedTokens += token
            amountSpent += token.state.contractState.quantity
            if (amountSpent > quantity) {
                break
            }
        }

        if (amountSpent < quantity) {
            throw CordaRuntimeException("Insufficient Funds.")
        }

        return Pair(amountSpent, selectedTokens)
    }
}
