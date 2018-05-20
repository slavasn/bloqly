package org.bloqly.machine.component

import org.bloqly.machine.model.Node
import org.bloqly.machine.service.NodeService
import org.bloqly.machine.vo.NodeListVO
import org.bloqly.machine.vo.TransactionListVO
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class NodeQueryService(

    private val serializationService: SerializationService,
    private val nodeService: NodeService,
    private val restTemplate: RestTemplate

) {

    private val log = LoggerFactory.getLogger(NodeQueryService::class.simpleName)

    fun queryForNodes() {

        val nodes = nodeService.getNodesToQuery()

        log.debug("Found ${nodes.size} hosts, start querying for nodes list")

        nodes.forEach { queryForNodes(it) }
    }

    private fun queryForNodes(node: Node) {

        val server = node.getServer()
        val path = "http://$server/nodes"

        log.debug("Query host $server for nodes")

        val nodeList = try {

            restTemplate.getForObject(path, NodeListVO::class.java)

        } catch (e: Exception) {

            log.error("Could not query path $path for nodes", e)

            NodeListVO(nodes = emptyList())
        }

        log.debug("Node $server returned  ${nodeList.nodes.size} nodes")

        nodeList.nodes.forEach {
            nodeService.addNode(
                    serializationService.nodeFromVO(it))
        }
    }

    fun sendTransactions(node: Node, transactionListVO: TransactionListVO) {

        val server = node.getServer()
        val path = "http://$server/transactions"

        try {

            restTemplate.postForObject(path, HttpEntity(transactionListVO), Void.TYPE)

        } catch (e: Exception) {

            log.error("Could not send transactions to $server", e)
        }
    }
}