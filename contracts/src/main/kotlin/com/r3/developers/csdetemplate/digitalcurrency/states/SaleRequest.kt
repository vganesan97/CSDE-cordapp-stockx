package com.r3.developers.csdetemplate.digitalcurrency.states

import com.r3.developers.csdetemplate.digitalcurrency.contracts.SaleRequestContract
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.UUID
import kotlin.reflect.jvm.internal.impl.descriptors.Visibilities.Public

@BelongsToContract(SaleRequestContract::class)
data class SaleRequest(
    val productId: UUID,
    val price: Double,
    val buyer: PublicKey,
    val owner: PublicKey,
    val accepted: Boolean = false,
    val participants: List<PublicKey> = listOf(buyer, owner)
) : ContractState {
    override fun getParticipants(): List<PublicKey> = participants
}
