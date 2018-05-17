package org.bloqly.machine.service

import com.google.common.primitives.Bytes.concat
import org.bloqly.machine.component.CryptoService
import org.bloqly.machine.model.Account
import org.bloqly.machine.model.Transaction
import org.bloqly.machine.model.TransactionType
import org.bloqly.machine.repository.TransactionRepository
import org.bloqly.machine.util.EncodingUtils
import org.bloqly.machine.util.EncodingUtils.decodeFromString
import org.bloqly.machine.util.EncodingUtils.encodeToString
import org.springframework.stereotype.Service

@Service
class TransactionService(

    private val cryptoService: CryptoService,
    private val transactionRepository: TransactionRepository

) {

    fun newTransaction(space: String,
                       origin: Account,
                       destination: Account,
                       self: Account? = null,
                       key: String? = null,
                       value: ByteArray,
                       transactionType: TransactionType,
                       referencedBlockId: String,
                       timestamp: Long

    ): Transaction {

        val dataToSign = concat(
                space.toByteArray(),
                origin.id.toByteArray(),
                destination.id.toByteArray(),
                value,
                referencedBlockId.toByteArray(),
                transactionType.name.toByteArray(),
                EncodingUtils.longToBytes(timestamp)
        )

        val privateKey = decodeFromString(origin.privateKey)

        val signature = cryptoService.sign(
                privateKey,
                cryptoService.digest(dataToSign)
        )

        val txHash = cryptoService.digest(signature)
        val transactionId = encodeToString(txHash)

        return Transaction(
                id = transactionId,
                space = space,
                origin = origin.id,
                destination = destination.id,
                self = self?.id,
                key = key,
                value = value,
                transactionType = transactionType,
                referencedBlockId = referencedBlockId,
                timestamp = timestamp,
                signature = signature,
                publicKey = origin.publicKey
        )
    }

    fun getNewTransactions(): List<Transaction> {
        // TODO: restrict by time
        return transactionRepository.findByContainingBlockIdIsNull();
    }
}