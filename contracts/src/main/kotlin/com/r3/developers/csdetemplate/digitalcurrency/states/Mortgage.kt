package com.r3.developers.csdetemplate.digitalcurrency.states

import com.r3.developers.csdetemplate.digitalcurrency.contracts.MortgageContract
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(MortgageContract::class)
data class Mortgage(
    val address: String,
    val owner: Party,
    val interestRate: Double,
    private val participants: List<PublicKey>) : ContractState {
    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    fun newOwner(newOwner: Party) =
        copy(owner = newOwner)
}