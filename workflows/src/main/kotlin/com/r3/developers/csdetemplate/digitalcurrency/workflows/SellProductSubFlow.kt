package com.r3.developers.csdetemplate.digitalcurrency.workflows

import CoinSelection
import com.r3.developers.csdetemplate.digitalcurrency.contracts.DigitalCurrencyContract
import com.r3.developers.csdetemplate.digitalcurrency.contracts.ProductContract
import com.r3.developers.csdetemplate.digitalcurrency.states.DigitalCurrency
import com.r3.developers.csdetemplate.digitalcurrency.states.Product
import com.r3.developers.csdetemplate.digitalcurrency.workflows.AbstractFlow.Companion.logger
import net.corda.v5.application.flows.*
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.UUID

@InitiatingFlow(protocol = "sell-product-protocol")
class SellProductSubFlow(
    val productId: UUID,
    val buyer: PublicKey,
    val price: Double) : SubFlow<String> {

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): String {
        logger.info("${this::class.java.enclosingClass}.call() called")

        try {
            val fromOwner = memberLookup.myInfo()

            val existingProduct = ledgerService.findUnconsumedStatesByType(Product::class.java)
            val productToSell = existingProduct.singleOrNull { product ->
                productId == product.state.contractState.productId
            } ?: throw CordaRuntimeException("Product with the provided ID not found.")

            if (productToSell.state.contractState.owner == buyer)
                throw CordaRuntimeException("Cannot sell product to self.")


            val toHolder = memberLookup.lookup(buyer) ?:
                throw CordaRuntimeException("MemberLookup can't find toHolder specified in flow arguments.")



            val soldProduct = productToSell.state.contractState.newOwner(toHolder.ledgerKeys.first())

            val notary = notaryLookup.notaryServices.single()

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(productToSell.ref)
                .addOutputState(soldProduct)
                .addCommand(ProductContract.Sell())
                .addSignatories(fromOwner.ledgerKeys.first(), toHolder.ledgerKeys.first())

            val session = flowMessaging.initiateFlow(toHolder.name
            ) { flowContextProperties: FlowContextProperties ->
                flowContextProperties.put("price", price.toString())
                flowContextProperties.put("buyer", buyer.toString())
            }

            logger.info("Seller sending TxBuilder to ${session.counterparty}")
            val updatedTxBuilder = ledgerService.sendAndReceiveTransactionBuilder(txBuilder, session)
            logger.info("Seller received TxBuilder from ${session.counterparty}")

            val signedTransaction = updatedTxBuilder.toSignedTransaction()

            logger.info("Seller finalizing Tx}")
            val finalizedSignedTransaction = ledgerService.finalize(signedTransaction, listOf(session))
            logger.info("Seller transaction finalized}")

            return finalizedSignedTransaction.transaction.id.toString().also {
                logger.info("Successful ${signedTransaction.commands.first()} with response: $it")
            }
        }
        catch (e: Exception) {
            logger.warn("Failed to process sell product for productId ${productId} with exception: '${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "sell-product-protocol")
class FinalizeSellProductResponderSubFlow: AbstractFlow(), ResponderFlow {

    @Suspendable
    override fun call(session: FlowSession) {
        logger.info("${this::class.java.enclosingClass}.call() called")

        try {
            logger.info("Buyer waiting to receive TxBuilder}")
            val proposedTxBuilder = ledgerService.receiveTransactionBuilder(session)
            logger.info("Buyer received TxBuilder}")
            val price = flowEngine.flowContextProperties.get("price") ?: throw CordaRuntimeException("Price not provided to buyer")
            val seller = memberLookup.myInfo()
            val buyer = memberLookup.lookup(session.counterparty) ?:
            throw CordaRuntimeException("MemberLookup can't find session counterparty.")

            val availableTokens = ledgerService.findUnconsumedStatesByType(DigitalCurrency::class.java)
            val coinSelection = CoinSelection()
            val (currencyToSpend, spentCurrency) = coinSelection.selectTokensForTransfer(price.toDouble(),
                buyer.ledgerKeys.first(),
                seller.ledgerKeys.first(),
                availableTokens)

            proposedTxBuilder.addInputStates(currencyToSpend.map { it.ref })
            proposedTxBuilder.addOutputStates(spentCurrency)
            proposedTxBuilder.addCommand(DigitalCurrencyContract.Transfer())

            logger.info("Buyer sending updated TxBuilder}")
            ledgerService.sendUpdatedTransactionBuilder(proposedTxBuilder, session)

            logger.info("Buyer waiting for finalization}")
            val finalizedSignedTransaction = ledgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.getOutputStates(Product::class.java).first() ?:
                throw CordaRuntimeException("Failed verification - transaction did not have at least one output ProductState.")

                logger.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            logger.info("Finished sell product responder flow - ${finalizedSignedTransaction.transaction.id}")
        }
        catch (e: Exception) {
            logger.warn("Sell Product responder flow failed with exception", e)
            throw e
        }
    }
}
