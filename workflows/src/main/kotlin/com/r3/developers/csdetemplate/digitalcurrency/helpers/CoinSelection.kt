package com.r3.developers.csdetemplate.digitalcurrency.helpers

import com.r3.developers.csdetemplate.digitalcurrency.states.DigitalCurrency
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.StateAndRef
import java.lang.reflect.Member

class CoinSelection @JvmOverloads constructor() {

    fun selectTokensForTransfer(quantity: Int,
                                sender: MemberX500Name,
                                recipient: MemberX500Name,
                                availableTokens: List<StateAndRef<DigitalCurrency>>):
            Pair<List<StateAndRef<DigitalCurrency>>, List<DigitalCurrency>> {
        // Simple (unoptimized) coin selection for learning purposes only
        // Send the rest of the other coins to receiver
        // Ignoring opportunity to merge currency
        val (amountSpent, selectedTokens) = selectTokens(quantity, availableTokens)
        val spentCurrency = selectedTokens.map { it.state.contractState.sendTo(recipient) }.toMutableList()

        // Send change back to sender
        if(amountSpent > quantity) {
            val overspend = amountSpent - quantity
            val change = spentCurrency.removeLast() //blindly turn last token into change
            spentCurrency.add(change.sendAmountTo(overspend, sender)) //change stays with sender
            spentCurrency.add(change.sendAmount(change.quantity-overspend))
        }


        return Pair(selectedTokens, spentCurrency)
    }

    fun selectTokensForRedemption(quantity: Int,
                                availableTokens: List<StateAndRef<DigitalCurrency>>):
            Pair<List<StateAndRef<DigitalCurrency>>, DigitalCurrency?> {
        val (amountSpent, currencyToWithdraw) = selectTokens(quantity, availableTokens)

        // Send change back to sender
        val remainingCurrency = if (amountSpent > quantity) {
            val change = amountSpent - quantity
            val lastDigitalCurrency = currencyToWithdraw.last() //blindly turn last token into change
            lastDigitalCurrency.state.contractState.sendAmount(change) //change stays with sender
        } else {
            null
        }

        return Pair(currencyToWithdraw, remainingCurrency)
    }

    private fun selectTokens(quantity: Int,
                             availableTokens: List<StateAndRef<DigitalCurrency>>)
                            : Pair<Int, List<StateAndRef<DigitalCurrency>>> {
        val selectedTokens = mutableListOf<StateAndRef<DigitalCurrency>>()
        var amountSpent = 0
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