package org.bloqly.machine.model

import org.bloqly.machine.util.EncodingUtils
import org.bloqly.machine.vo.VoteVO
import javax.persistence.Column
import javax.persistence.EmbeddedId
import javax.persistence.Entity

@Entity
data class Vote(

    @EmbeddedId
    val id: VoteId,

    @Column(nullable = false)
    val blockId: String,

    @Column(nullable = false)
    val round: Long,

    @Column(nullable = false)
    val proposerId: String,

    @Column(nullable = false)
    val timestamp: Long,

    @Column(nullable = false)
    val signature: ByteArray,

    @Column(nullable = false)
    val publicKey: String
) {

    fun toVO(): VoteVO {

        return VoteVO(
            validatorId = id.validatorId,
            space = id.space,
            height = id.height,
            blockId = blockId,
            round = round,
            proposerId = proposerId,
            timestamp = timestamp,
            signature = EncodingUtils.encodeToString16(signature),
            publicKey = publicKey
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vote

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}