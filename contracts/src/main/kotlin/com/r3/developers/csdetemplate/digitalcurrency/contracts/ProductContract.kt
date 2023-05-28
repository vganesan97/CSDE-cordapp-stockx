package com.r3.developers.csdetemplate.digitalcurrency.contracts

import com.r3.developers.csdetemplate.digitalcurrency.states.Mortgage
import com.r3.developers.csdetemplate.digitalcurrency.states.Product
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class ProductContract: Contract {

    class Create: Command
    class Sell: Command

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.commands.firstOrNull { it is Create || it is Sell }
            ?: throw CordaRuntimeException("Requires a single Product command")
/**/
        when(command) {
            is Create -> {
                "When command is Create there should be no input states." using (transaction.inputContractStates.isEmpty())
                "When command is Create there should be one and only one output state." using (transaction.outputContractStates.size == 1)

                "The output state should have only 1 participant." using {
                    val output = transaction.outputContractStates.filterIsInstance<Product>().first()
                    output.participants.size==1
                }
            }
            is Sell -> {
                "When command is Sell there should be at least two input states." using (transaction.inputContractStates.size >= 2)
                "When command is Sell there should be at least two output states." using (transaction.outputContractStates.size >= 2)

                val sentMortgage = transaction.inputContractStates.filterIsInstance<Mortgage>().first()
                val receivedMortgage = transaction.outputContractStates.filterIsInstance<Mortgage>().first()
                "When command is Sell the new owner should be different than the current owner." using (
                        sentMortgage.owner != receivedMortgage.owner)

                "When command is Sell there must be exactly one participants." using (
                        transaction.outputContractStates.all { it.participants.size == 1 })
            }
            is Sell -> {
                "When command is Sell there should be at least two input states." using (transaction.inputContractStates.size >= 2)
                "When command is Sell there should be at least two output states." using (transaction.outputContractStates.size >= 2)

                val sentProduct = transaction.inputContractStates.filterIsInstance<Product>().first()
                val receivedProduct = transaction.outputContractStates.filterIsInstance<Product>().first()
                "When command is Sell the new owner should be different than the current owner." using (
                        sentProduct.owner != receivedProduct.owner)

                "When command is Sell there must be exactly one participants." using (
                        transaction.outputContractStates.all { it.participants.size == 1 })
            }
//            is Bundle -> {
//                val targetMortgages = transaction.inputContractStates.filterIsInstance<Mortgage>()
//                "At least one target mortgage has already been bundled." using targetMortgages.all { mortgage -> !mortgage.bundled }
//            }
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