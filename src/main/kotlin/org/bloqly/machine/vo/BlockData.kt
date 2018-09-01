package org.bloqly.machine.vo

import org.bloqly.machine.annotation.ValueObject
import org.bloqly.machine.model.Block

@ValueObject
data class BlockData(

    val block: BlockVO,

    val transactions: List<TransactionVO>,

    val votes: List<VoteVO>

) {
    constructor(
        block: Block
    ) : this(block.toVO(), block.transactions.map { it.toVO() }, block.votes.map { it.toVO() })

    fun toModel(): Block {
        val block = block.toModel()

        val txs = transactions.map { it.toModel() }

        val votes = votes.map { it.toModel() }

        return block.copy(votes = votes, transactions = txs)
    }
}