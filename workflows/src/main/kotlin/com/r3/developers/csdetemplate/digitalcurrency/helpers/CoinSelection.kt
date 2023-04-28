package com.r3.developers.csdetemplate.digitalcurrency.helpers

import com.r3.developers.csdetemplate.digitalcurrency.states.DigitalCurrency
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService

class CoinSelection @JvmOverloads constructor() {

    fun selectTokens(quantity: Int, availableTokens: List<StateAndRef<DigitalCurrency>>): Pair<Int, List<StateAndRef<DigitalCurrency>>> {
        // Simple (unoptimized) coin selection for learning purposes only
        val selectedTokens = mutableListOf<StateAndRef<DigitalCurrency>>()
        var amountSelected = 0
        for (token in availableTokens) {
            selectedTokens += token
            amountSelected += token.state.contractState.quantity
            if (amountSelected > quantity) {
                break
            }
        }

        if (amountSelected < quantity) {
            throw CordaRuntimeException("Insufficient Funds.")
        }

        return Pair(amountSelected, selectedTokens)
    }
}