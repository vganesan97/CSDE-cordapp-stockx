package com.r3.developers.csdetemplate.digitalcurrency.contracts

import com.r3.developers.csdetemplate.digitalcurrency.states.Mortgage
import com.r3.developers.csdetemplate.digitalcurrency.states.Product
import com.r3.developers.csdetemplate.digitalcurrency.states.SaleRequest
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class SaleRequestContract: Contract {

    class Create: Command
    class Accept: Command
    class Deny: Command

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.commands.firstOrNull{ it is Accept || it is Deny}
            ?: throw CordaRuntimeException("Requires a single SaleRequest command")

        when(command) {
            is Create -> {
                "There must be one input state and two output states for Create command" using
                        (transaction.inputContractStates.size == 1 && transaction.outputContractStates.size == 2)

                val inputProduct = transaction.inputContractStates.filterIsInstance<Product>().first()
                val outputProduct = transaction.outputContractStates.filterIsInstance<Product>().first()
                val outputSaleRequest = transaction.outputContractStates.filterIsInstance<SaleRequest>().first()

                "The product's saleRequested field must not be true in the input state" using (!inputProduct.saleRequested)
                "The product's saleRequested field must be true in the output state" using (outputProduct.saleRequested)
                "The productId in the SaleRequest must match the productId in the ProductState" using (outputSaleRequest.productId == outputProduct.productId)

                "Sale price must be positive" using (outputSaleRequest.price > 0)
                "Buyer and seller must be different" using (outputSaleRequest.buyer != outputSaleRequest.owner)
                "Buyer and seller must be participants" using (outputSaleRequest.participants.containsAll(listOf(outputSaleRequest.buyer, outputSaleRequest.owner)))
            }

            is Accept -> {
                "There must be one input state for Accept command" using (transaction.inputContractStates.size == 1)
                "There must be one output state for Accept command" using (transaction.outputContractStates.size == 1)

                val saleRequest = transaction.inputContractStates.filterIsInstance<SaleRequest>().first()
                val acceptedSaleRequest = transaction.outputContractStates.filterIsInstance<SaleRequest>().first()

                "Only the owner can accept the sale" using (transaction.signatories.contains(acceptedSaleRequest.owner))
                "Sale request must not have been previously accepted" using (!saleRequest.accepted)
                "Accepted sale request must be marked as accepted" using (acceptedSaleRequest.accepted)
                "Transaction must be signed by all necessary parties" using (transaction.signatories.contains(saleRequest.owner))
            }
            is Deny -> {
                "There must be two input states for Deny command" using (transaction.inputContractStates.size == 2)
                "There must be one output state for Deny command" using (transaction.outputContractStates.size == 1)

                val inputSaleRequest = transaction.inputContractStates.filterIsInstance<SaleRequest>().first()
                val inputProductState = transaction.inputContractStates.filterIsInstance<Product>().first()
                val outputProductState = transaction.outputContractStates.filterIsInstance<Product>().first()

                "Sale request must not have been previously accepted" using (!inputSaleRequest.accepted)
                "ProductState for sale must be marked as requested for sale" using (inputProductState.saleRequested)
                "ProductState after denial must not be requested for sale" using (!outputProductState.saleRequested)
                "Only the owner can deny the sale" using (transaction.signatories.containsAll(listOf(
                    inputSaleRequest.owner, inputProductState.owner)))

                // No need to check if the state is unspent, Corda does this
            }
            else -> {
                throw CordaRuntimeException("Command ${command} not allowed.")
            }
        }
    }

    // Helper function to allow writing constraints in the Corda 4 '"text" using (boolean)' style
    private infix fun String.using(expr: Boolean) {
        if (!expr) throw CordaRuntimeException("Failed requirement: $this")
    }

    // Helper function to allow writing constraints in '"text" using {lambda}' style where the last expression
    // in the lambda is a boolean.
    private infix fun String.using(expr: () -> Boolean) {
        if (!expr.invoke()) throw CordaRuntimeException("Failed requirement: $this")
    }
}