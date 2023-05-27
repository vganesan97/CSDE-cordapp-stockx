package com.r3.developers.csdetemplate.digitalcurrency.states

import com.r3.developers.csdetemplate.digitalcurrency.contracts.ProductContract
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.*

@BelongsToContract(ProductContract::class)
data class Product (
    val owner: PublicKey,
    val productId: UUID,
    val name: String,
    val listingDetails: String,
    val condition: String,
    val price: Double,
    private val participants: List<PublicKey>) : ContractState {
    override fun getParticipants(): List<PublicKey> {
        return listOf(owner)
    }

    fun newOwner(newOwner: PublicKey) =
        copy(owner = newOwner)

}