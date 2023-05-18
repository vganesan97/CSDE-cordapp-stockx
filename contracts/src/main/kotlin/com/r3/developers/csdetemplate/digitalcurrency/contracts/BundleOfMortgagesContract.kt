package com.r3.developers.csdetemplate.digitalcurrency.contracts

import com.r3.developers.csdetemplate.digitalcurrency.states.Mortgage
import com.r3.developers.csdetemplate.digitalcurrency.states.BundleOfMortgages
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class BundleOfMortgagesContract: Contract {

    class Create: Command
    class Sell: Command
    class Payoff: Command

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.commands.firstOrNull { it is Create || it is Sell || it is Payoff }
            ?: throw CordaRuntimeException("Requires a single Bundle of Mortgages command")

        when(command) {
            is Create -> {
                "When command is Create there should at least one input states." using (transaction.inputContractStates.size >= 1)
                "When command is Create there should be one and only one output state." using (transaction.outputContractStates.size == 1)

                "The output state should have only 1 participant." using {
                    val output = transaction.outputContractStates.first() as BundleOfMortgages
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
            is Payoff -> {
                //TODO
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