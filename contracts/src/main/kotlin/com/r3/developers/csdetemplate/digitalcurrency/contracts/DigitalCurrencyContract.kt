package com.r3.developers.csdetemplate.digitalcurrency.contracts

import com.r3.developers.csdetemplate.digitalcurrency.states.DigitalCurrency
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class DigitalCurrencyContract: Contract {

    class Issue: Command
    class Transfer: Command
    class Burn: Command

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.commands.singleOrNull() ?: throw CordaRuntimeException("Requires a single command")

        when(command) {
            is Issue -> {
                "When command is Issue there should be no input states." using (transaction.inputContractStates.isEmpty())
                "When command is Issue there should be one and only one output state." using (transaction.outputContractStates.size == 1)

                "The output state should have two and only two participants." using {
                    val output = transaction.outputContractStates.first() as DigitalCurrency
                    output.participants.size==2
                }
            }
            is Transfer -> {
                "When command is Transfer there should be at least one input state." using (transaction.inputContractStates.size >= 1)
                "When command is Transfer there should be at least one output state." using (transaction.outputContractStates.size >= 1)

                val sentDigitalCurrency = transaction.inputContractStates as List<DigitalCurrency>
                val receivedDigitalCurrency = transaction.outputContractStates as List<DigitalCurrency>
                val sentAmount = sentDigitalCurrency.sumOf { it.quantity }
                val receivedAmount = receivedDigitalCurrency.sumOf { it.quantity }
                "When command is Transfer the sent and received amount should be the same total amount." using (
                    sentAmount == receivedAmount)

                "When command is Transfer there must be exactly two participants." using (
                        receivedDigitalCurrency.all { it.participants.size == 2 })
                // additional checks for sender/receiver being the specific participants
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