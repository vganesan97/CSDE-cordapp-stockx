package com.r3.developers.csdetemplate.digitalcurrency.workflows

import com.r3.developers.csdetemplate.digitalcurrency.contracts.MortgageContract
import com.r3.developers.csdetemplate.digitalcurrency.contracts.ProductContract
import com.r3.developers.csdetemplate.digitalcurrency.states.Product
import net.corda.v5.application.flows.*
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class CreateProduct(val owner: String,
                         val name: String,
                         val condition: String,
                         val price: Double,
                         val listingDetails: String)

@InitiatingFlow(protocol = "finalize-create-product-flow")
class CreateProductFlow(): AbstractFlow(), ClientStartableFlow {

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        logger.info("${this::class.java.enclosingClass}.call() called")
        try {
            val flowArgs = requestBody.getRequestBodyAs(json, CreateProduct::class.java)
            val myInfo = memberLookup.myInfo()
            val owner =  memberLookup.lookup(MemberX500Name.parse(flowArgs.owner)) ?:
                throw CordaRuntimeException("MemberLookup can't find owner specified in flow arguments.")

            val allNodePks = memberLookup.lookup()
                .filter { !it.name.toString().contains("NotaryRep1") }
                .map { it.ledgerKeys.first() }
            val allNodes = memberLookup.lookup()

            logger.warn("all node pks: ${allNodePks}")
            val product = Product(owner.ledgerKeys.first(),
                productId = UUID.randomUUID(),
                flowArgs.name,
                flowArgs.listingDetails,
                flowArgs.condition,
                flowArgs.price,
                participants = allNodePks)

            logger.warn("prod participants: ${product.participants}")

            val notary = notaryLookup.notaryServices.single()
            val signatories = mutableListOf<PublicKey>(myInfo.ledgerKeys.first()).union(product.participants)

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(product)
                .addCommand(ProductContract.Create())
                .addSignatories(signatories)

            // List to hold all the flow sessions
            val sessions = mutableListOf<FlowSession>()

            // Initiate a flow session with each participant
            for (participant in product.participants) {
                logger.warn("participant: ${participant}")
                val node = memberLookup.lookup(participant)
                if (node != null) {
                    logger.warn("flow session initiated for ${node.name}")
                    sessions.add(flowMessaging.initiateFlow(node.name))
                }
            }

//            // Finalize the transaction
//            val finalizedSignedTransaction = ledgerService.finalize(
//                signedTransaction,
//                sessions
//            )
//
//            val signedTransaction = txBuilder.toSignedTransaction()
//
//            val session = flowMessaging.initiateFlow(owner.name)
//
//            val finalizedSignedTransaction = ledgerService.finalize(
//                signedTransaction,
//                listOf(session)
//            )

            val signedTransaction = txBuilder.toSignedTransaction()

            // Sign the transaction and collect signatures from all participants
            val finalizedSignedTransaction = ledgerService.finalize(signedTransaction, sessions)

            return finalizedSignedTransaction.transaction.id.toString().also {
                logger.info("Successful ${signedTransaction.commands.first()} with response: $it")
            }
        }
        catch (e: Exception) {
            logger.warn("create product failed")
            throw e
        }
    }
}

@InitiatedBy(protocol = "finalize-create-product-flow")
class FinalizeCreateProductResponderFlow: AbstractFlow(), ResponderFlow {

    @Suspendable
    override fun call(session: FlowSession) {
        logger.info("${this::class.java.enclosingClass}.call() called")

        try {
            val finalizedSignedTransaction = ledgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.getOutputStates(Product::class.java).singleOrNull() ?:
                throw CordaRuntimeException("Failed verification - transaction did not have exactly one output Product.")

                logger.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            logger.info("Finished create product responder flow - ${finalizedSignedTransaction.transaction.id}")
        }
        catch (e: Exception) {
            logger.warn("Create Product responder flow failed with exception", e)
            throw e
        }
    }
}

