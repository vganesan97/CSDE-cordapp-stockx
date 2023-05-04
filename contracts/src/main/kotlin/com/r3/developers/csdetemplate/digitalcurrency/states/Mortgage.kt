package com.r3.developers.csdetemplate.digitalcurrency.states

import com.r3.developers.csdetemplate.digitalcurrency.contracts.MortgageContract
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(MortgageContract::class)
data class Mortgage(
    val address: String,
    val owner: MemberX500Name,
    val interestRate: Double,
    private val participants: List<PublicKey>) : ContractState {
    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    fun newOwner(newOwner: MemberX500Name) =
        copy(owner = newOwner)
}