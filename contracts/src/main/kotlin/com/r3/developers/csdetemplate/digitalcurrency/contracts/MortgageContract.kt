package com.r3.developers.csdetemplate.digitalcurrency.contracts

import com.r3.developers.csdetemplate.digitalcurrency.states.DigitalCurrency
import com.r3.developers.csdetemplate.digitalcurrency.states.Mortgage
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class MortgageContract: Contract {

    class Issue: Command
    class Transfer: Command
    class Payoff: Command

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.commands.singleOrNull() ?: throw CordaRuntimeException("Requires a single command")

        when(command) {
            is MortgageContract.Issue -> {
                "When command is Issue there should be no input states." using (transaction.inputContractStates.isEmpty())
                "When command is Issue there should be one and only one output state." using (transaction.outputContractStates.size == 1)

                "The output state should have two and only two participants." using {
                    val output = transaction.outputContractStates.first() as Mortgage
                    output.participants.size==2
                }
            }
            is MortgageContract.Transfer -> {
                "When command is Transfer there should be exactly one input state." using (transaction.inputContractStates.size == 1)
                "When command is Transfer there should be exactly one output state." using (transaction.outputContractStates.size == 1)

                val sentMortgage = transaction.inputContractStates.first() as Mortgage
                val receivedMortgage = transaction.outputContractStates.first() as Mortgage
                "When command is Transfer the new owner should be different than the current owner." using (
                        sentMortgage.owner != receivedMortgage.owner)

                "When command is Transfer there must be exactly two participants." using (
                        transaction.outputContractStates.all { it.participants.size == 2 })
            }
            is MortgageContract.Payoff -> {
                "When command is Withdraw there should be at least one input state." using (transaction.inputContractStates.size >= 1)
                "When command is Withdraw there should be no output states." using (transaction.outputContractStates.size == 0)
            }
            else -> {
                throw CordaRuntimeException("Command not allowed.")
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