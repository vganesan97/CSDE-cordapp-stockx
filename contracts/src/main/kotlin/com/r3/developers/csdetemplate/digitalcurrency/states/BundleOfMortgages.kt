package com.r3.developers.csdetemplate.digitalcurrency.states

import com.r3.developers.csdetemplate.digitalcurrency.contracts.BundleOfMortgagesContract
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.*

@BelongsToContract(BundleOfMortgagesContract::class)
data class BundleOfMortgages(
    val bundleId: UUID,
    val originator: PublicKey,
    val mortgageIds: List<UUID>,
    private val participants: List<PublicKey>) : ContractState {
    override fun getParticipants(): List<PublicKey> {
        return listOf(originator)
    }
}