package com.r3.developers.csdetemplate.digitalcurrency.states

import com.r3.developers.csdetemplate.digitalcurrency.contracts.DigitalCurrencyContract
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(DigitalCurrencyContract::class)
data class DigitalCurrency(
    val quantity: Int,
    val holder: MemberX500Name,
    private val participants: List<PublicKey>) : ContractState {
    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    fun sendAmount(send: Int) =
        copy(quantity = send)

    fun sendTo(newHolder: MemberX500Name) =
        copy(holder = newHolder)

    fun sendAmountTo(send: Int, newHolder: MemberX500Name) =
        copy(quantity = send, holder = newHolder)

}