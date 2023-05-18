package com.r3.developers.csdetemplate.digitalcurrency.states

import com.r3.developers.csdetemplate.digitalcurrency.contracts.MortgageContract
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.*

@BelongsToContract(MortgageContract::class)
data class Mortgage(
    val address: String,
    val mortgageId: UUID,
    val owner: PublicKey,
    val interestRate: Double,
    val fixedInterestRate: Boolean,
    val loanToValue: Double,
    val condition: String,
    val creditQualityRating: String,
    val listingDetails: String,
    private val participants: List<PublicKey>) : ContractState {
    override fun getParticipants(): List<PublicKey> {
        return listOf(owner)
    }

    fun newOwner(newOwner: PublicKey) =
        copy(owner = newOwner)
}