package org.bloqly.machine.component

import org.bloqly.machine.model.Transaction
import org.bloqly.machine.model.Vote
import org.bloqly.machine.service.NodeService
import org.bloqly.machine.vo.BlockData
import org.bloqly.machine.vo.Delta
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ExecutorService

@Service
class EventSenderService(
    private val nodeService: NodeService,
    private val nodeQueryService: NodeQueryService,
    private val executorService: ExecutorService,
    private val blockProcessor: BlockProcessor
) {

    private val log = LoggerFactory.getLogger(EventSenderService::class.simpleName)

    fun sendVotes(votes: List<Vote>) {
        val nodes = nodeService.getNodesToQuery()

        nodes.forEach { node ->
            executorService.submit {
                try {
                    log.info("Sending votes to node $node")
                    nodeQueryService.sendVotes(node, votes)
                } catch (e: Exception) {
                    log.error("Could not send votes to $node. Details: ${e.message}")
                }
            }
        }
    }

    fun sendTransactions(transactions: List<Transaction>) {
        val nodes = nodeService.getNodesToQuery()

        nodes.forEach { node ->
            executorService.submit {
                try {
                    log.info("Sending transactions to node $node")
                    nodeQueryService.sendTransactions(node, transactions)
                } catch (e: Exception) {
                    log.error("Could not send transactions to $node. Details: ${e.message}")
                }
            }
        }
    }

    fun sendProposals(proposals: List<BlockData>) {
        val nodes = nodeService.getNodesToQuery()

        nodes.forEach { node ->
            executorService.submit {
                try {
                    log.info("Sending proposals to node $node")
                    nodeQueryService.sendProposals(node, proposals)
                } catch (e: Exception) {
                    "Could not send proposals to $node. Details: ${e.message}"
                }
            }
        }
    }

    fun requestDeltas(deltas: List<Delta>) {
        val nodes = nodeService.getNodesToQuery()
        val node = nodes.shuffled().first()

        deltas.forEach { delta ->
            executorService.submit {
                try {
                    log.info("Request deltas from node $node")

                    nodeQueryService.requestDelta(node, delta)
                        ?.sortedBy { it.block.height }
                        ?.forEach { blockProcessor.processBlock(it) }
                } catch (e: Exception) {
                    "Could not request deltas from $node. Details: ${e.message}"
                }
            }
        }
    }
}