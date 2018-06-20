package org.bloqly.machine.vo

import org.bloqly.machine.annotation.ValueObject
import org.bloqly.machine.model.Vote
import org.bloqly.machine.model.VoteId
import org.bloqly.machine.util.EncodingUtils

@ValueObject
data class VoteVO(
    val validatorId: String,
    val space: String,
    val height: Long,
    val blockId: String,
    val round: Long,
    val proposerId: String,
    val timestamp: Long,
    val signature: String,
    val publicKey: String
) {

    fun toModel(): Vote {

        val voteId = VoteId(
            validatorId = validatorId,
            space = space,
            height = height
        )

        return Vote(
            id = voteId,
            blockId = blockId,
            round = round,
            proposerId = proposerId,
            timestamp = timestamp,
            signature = EncodingUtils.decodeFromString16(signature),
            publicKey = publicKey
        )
    }
}
