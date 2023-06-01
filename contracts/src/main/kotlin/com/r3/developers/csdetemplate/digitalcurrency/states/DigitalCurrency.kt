package com.r3.developers.csdetemplate.digitalcurrency.states

import com.r3.developers.csdetemplate.digitalcurrency.contracts.DigitalCurrencyContract
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(DigitalCurrencyContract::class)
data class DigitalCurrency(
    val quantity: Double,
    val holder: PublicKey,
    private val participants: List<PublicKey>) : ContractState {
    override fun getParticipants(): List<PublicKey> {
        return listOf(holder)
    }

    fun sendAmount(send: Double) =
        copy(quantity = send)

    fun sendTo(newHolder: PublicKey) =
        copy(holder = newHolder)

    fun sendAmountTo(send: Double, newHolder: PublicKey) =
        copy(quantity = send, holder = newHolder)

}