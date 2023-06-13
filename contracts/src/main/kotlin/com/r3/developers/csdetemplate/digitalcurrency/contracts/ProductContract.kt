package com.r3.developers.csdetemplate.digitalcurrency.contracts

import com.r3.developers.csdetemplate.digitalcurrency.states.Mortgage
import com.r3.developers.csdetemplate.digitalcurrency.states.Product
import com.r3.developers.csdetemplate.digitalcurrency.states.SaleRequest
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.slf4j.LoggerFactory

class ProductContract: Contract {

    class Create: Command
    class Sell: Command
    class AcceptRequestSale: Command
    class DenyRequestSale: Command
    class Auction: Command

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.commands.firstOrNull {
            it is Create ||
            it is Sell ||
            it is AcceptRequestSale ||
            it is DenyRequestSale ||
            it is Auction
        } ?: throw CordaRuntimeException("Requires a single Product command")

        when(command) {
            is Create -> {
                "When command is Create there should be no input states." using (transaction.inputContractStates.isEmpty())
                //"When command is Create there should be one and only one output state." using (transaction.outputContractStates.size == 1)

//                "The output state should have only 1 participant." using {
//                    val output = transaction.outputContractStates.filterIsInstance<Product>().first()
//                    output.participants.size==1
//                }
            }
            is AcceptRequestSale -> {
                "When command is RequestSale there should be one input state." using (transaction.inputContractStates.size == 1)
                "When command is RequestSale there should be two output states." using (transaction.outputContractStates.size == 2)

                val inputProduct = transaction.inputContractStates.filterIsInstance<Product>().first()
                val outputProduct = transaction.outputContractStates.filterIsInstance<Product>().first()
                val outputSaleRequest = transaction.outputContractStates.filterIsInstance<SaleRequest>().first()

                "When command is RequestSale the input product should have saleRequested = false." using (inputProduct.saleRequested == false)
                "When command is RequestSale the output product should have saleRequested = true." using (outputProduct.saleRequested == true)
                "When command is RequestSale the output SaleRequest should have the same productId as the Product." using (outputProduct.productId == outputSaleRequest.productId)
            }
            is DenyRequestSale -> {
                "When command is DenyRequestSale there should be two input states." using (transaction.inputContractStates.size == 2)
                "When command is DenyRequestSale there should be one output state." using (transaction.outputContractStates.size == 1)

                val inputProduct = transaction.inputContractStates.filterIsInstance<Product>().first()
                val inputSaleRequest = transaction.inputContractStates.filterIsInstance<SaleRequest>().first()
                val outputProduct = transaction.outputContractStates.filterIsInstance<Product>().first()

                "When command is DenyRequestSale the input product should have saleRequested = true." using (inputProduct.saleRequested == true)
                "When command is DenyRequestSale the output product should have saleRequested = false." using (outputProduct.saleRequested == false)
                "When command is DenyRequestSale the output product should have the same productId as the input product." using (outputProduct.productId == inputProduct.productId)
            }

            is Auction -> {

            }
            is Sell -> {
                "When command is Sell there should be at least two input states." using (transaction.inputContractStates.size >= 2)
                "When command is Sell there should be at least two output states." using (transaction.outputContractStates.size >= 2)

                val sentProduct = transaction.inputContractStates.filterIsInstance<Product>().first()
                val receivedProduct = transaction.outputContractStates.filterIsInstance<Product>().first()
                "When command is Sell the new owner should be different than the current owner." using (
                        sentProduct.owner != receivedProduct.owner)
               // log.warn("participants: ${transaction.outputContractStates.map { it.participants }}")
               // "When command is Sell there must be exactly two participants." using (transaction.outputContractStates.all { it.participants.size == 2 })
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