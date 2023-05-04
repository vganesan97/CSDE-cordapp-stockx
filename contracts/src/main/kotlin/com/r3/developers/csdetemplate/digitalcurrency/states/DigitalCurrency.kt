package com.r3.developers.csdetemplate.digitalcurrency.states

import com.r3.developers.csdetemplate.digitalcurrency.contracts.DigitalCurrencyContract
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(DigitalCurrencyContract::class)
data class DigitalCurrency(
    val quantity: Int,
    val holder: Party,
    private val participants: List<PublicKey>) : ContractState {
    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    fun sendAmount(send: Int) =
        copy(quantity = send)

    fun sendTo(newHolder: Party) =
        copy(holder = newHolder)

    fun sendAmountTo(send: Int, newHolder: Party) =
        copy(quantity = send, holder = newHolder)

}