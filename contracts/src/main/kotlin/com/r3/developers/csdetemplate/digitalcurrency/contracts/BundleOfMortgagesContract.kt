package com.r3.developers.csdetemplate.digitalcurrency.contracts

import com.r3.developers.csdetemplate.digitalcurrency.states.BundleOfMortgages
import com.r3.developers.csdetemplate.digitalcurrency.states.Mortgage
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class BundleOfMortgagesContract: Contract {

    class Create: Command

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.commands.firstOrNull { it is Create }
            ?: throw CordaRuntimeException("Requires a single Bundle of Mortgages command")

        when(command) {
            is Create -> {
                "When command is Create there should be at least one output state." using (transaction.outputContractStates.size >= 1)

                val targetMortgages = transaction.inputContractStates.filterIsInstance<Mortgage>()
                "At least one target mortgage has already been bundled." using targetMortgages.all { mortgage -> !mortgage.bundled }

                "The output state should have only 1 participant." using {
                    val output = transaction.outputContractStates.first() as BundleOfMortgages
                    output.participants.size==1
                }
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