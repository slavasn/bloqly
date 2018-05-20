package org.bloqly.machine.service

import org.bloqly.machine.component.EventSenderService
import org.bloqly.machine.component.NodeQueryService
import org.bloqly.machine.vo.BlockDataVO
import org.bloqly.machine.vo.TransactionListVO
import org.bloqly.machine.vo.VoteVO
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("production")
class NetworkEventSenderService(

    private val nodeService: NodeService,
    private val nodeQueryService: NodeQueryService

) : EventSenderService {

    override fun sendVotes(votes: List<VoteVO>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sendTransactions(transactionListVO: TransactionListVO) {
        val nodes = nodeService.getNodesToQuery()

        nodes.forEach { node ->
            nodeQueryService.sendTransactions(node, transactionListVO)
        }
    }

    override fun sendProposals(proposals: List<BlockDataVO>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}