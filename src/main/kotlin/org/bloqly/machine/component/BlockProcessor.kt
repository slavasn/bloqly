package org.bloqly.machine.component

import org.bloqly.machine.Application.Companion.MAX_REFERENCED_BLOCK_DEPTH
import org.bloqly.machine.model.Account
import org.bloqly.machine.model.Block
import org.bloqly.machine.model.InvocationResult
import org.bloqly.machine.model.Properties
import org.bloqly.machine.model.Transaction
import org.bloqly.machine.model.TransactionOutput
import org.bloqly.machine.model.TransactionOutputId
import org.bloqly.machine.model.Vote
import org.bloqly.machine.repository.AccountRepository
import org.bloqly.machine.repository.BlockRepository
import org.bloqly.machine.repository.PropertyService
import org.bloqly.machine.repository.TransactionOutputRepository
import org.bloqly.machine.repository.TransactionRepository
import org.bloqly.machine.repository.VoteRepository
import org.bloqly.machine.service.AccountService
import org.bloqly.machine.service.BlockService
import org.bloqly.machine.service.ContractService
import org.bloqly.machine.service.TransactionService
import org.bloqly.machine.service.VoteService
import org.bloqly.machine.util.CryptoUtils
import org.bloqly.machine.util.ObjectUtils
import org.bloqly.machine.util.decode16
import org.bloqly.machine.vo.BlockData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation.SERIALIZABLE
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private data class TransactionResult(
    val transaction: Transaction,
    val invocationResult: InvocationResult
)

@Service
@Transactional(isolation = SERIALIZABLE)
// TODO add test for rejected transaction
class BlockProcessor(
    private val transactionRepository: TransactionRepository,
    private val transactionService: TransactionService,
    private val voteService: VoteService,
    private val voteRepository: VoteRepository,
    private val blockService: BlockService,
    private val blockRepository: BlockRepository,
    private val transactionProcessor: TransactionProcessor,
    private val propertyService: PropertyService,
    private val contractService: ContractService,
    private val transactionOutputRepository: TransactionOutputRepository,
    private val accountService: AccountService,
    private val accountRepository: AccountRepository
) {

    private val log: Logger = LoggerFactory.getLogger(BlockProcessor::class.simpleName)

    @Transactional
    fun processReceivedBlock(blockData: BlockData) {

        val receivedBlock = blockData.block.toModel()

        if (!blockService.isAcceptable(receivedBlock)) {
            return
        }

        requireValid(receivedBlock)

        val votes = blockData.votes.map { voteVO ->
            val vote = voteVO.toModel(accountService.getAccountByPublicKey(voteVO.publicKey))
            voteService.validateAndSave(vote)
        }

        val transactions = blockData.transactions.map {
            transactionRepository.save(it.toModel())
        }

        val propertyContext = PropertyContext(propertyService, contractService)

        val currentLIB = blockService.getLIBForSpace(receivedBlock.spaceId)

        evaluateBlocks(currentLIB, receivedBlock, propertyContext)

        val block = receivedBlock.copy(
            transactions = transactions,
            votes = votes
        )

        evaluateBlock(block, propertyContext)

        saveBlock(block)

        moveLIBIfNeeded(currentLIB)
    }

    private fun saveBlock(block: Block): Block {
        require(blockService.isAcceptable(block)) {
            // TODO improve block logging, add producer etc
            "Block is not acceptable: ${block.hash}"
        }
        return blockRepository.save(block)
    }

    private fun evaluateBlocks(currentLIB: Block, toBlock: Block, propertyContext: PropertyContext) {

        if (currentLIB == toBlock) {
            return
        }

        getBlocksRange(currentLIB, toBlock).forEach { block ->
            evaluateBlock(block, propertyContext)
        }
    }

    @Transactional(readOnly = true)
    fun getLastPropertyValue(
        space: String,
        self: String,
        target: String,
        key: String
    ): ByteArray? {
        val currentLIB = blockService.getLIBForSpace(space)
        val lastBlock = blockService.getLastBlockForSpace(space)

        val propertyContext = PropertyContext(propertyService, contractService)

        evaluateBlocks(currentLIB, lastBlock, propertyContext)

        return propertyContext.getPropertyValue(space, self, target, key)
    }

    /**
     * Returns blocks range (afterBlock, toBlock]
     */
    @Transactional(readOnly = true)
    internal fun getBlocksRange(afterBlock: Block, toBlock: Block): List<Block> {

        var currentBlock = toBlock

        val blocks = mutableListOf<Block>()

        while (currentBlock.height > afterBlock.height) {
            blocks.add(currentBlock)

            currentBlock = blockRepository.findByHash(currentBlock.parentHash)!!
        }

        return blocks.reversed()
    }

    private fun evaluateBlock(block: Block, propertyContext: PropertyContext) {

        block.transactions.forEach { tx ->

            // already processed this transaction?
            val txOutput = transactionOutputRepository
                .findById(TransactionOutputId(block.hash, tx.hash))

            val output = if (txOutput.isPresent) {
                ObjectUtils.readProperties(txOutput.get().output)
            } else {
                val invocationResult = transactionProcessor.processTransaction(tx, propertyContext)

                require(invocationResult.isOK()) {
                    "Could not process transaction $tx"
                }

                saveTxOutputs(listOf(TransactionResult(tx, invocationResult)), block)

                invocationResult.output
            }

            propertyContext.updatePropertyValues(output)
        }
    }

    private fun requireValid(block: Block) {

        val producer = accountRepository.findByAccountId(block.producerId)!!

        require(CryptoUtils.verifyBlock(block, producer.publicKey.decode16())) {
            "Cold not verify block ${block.hash}"
        }
    }

    @Transactional
    fun createNextBlock(spaceId: String, producer: Account, passphrase: String, round: Long): BlockData {

        blockRepository.findBySpaceIdAndProducerIdAndRound(spaceId, producer.accountId, round)
            ?.let { return BlockData(it) }

        val lastBlock = blockService.getLastBlockForSpace(spaceId)
        val newHeight = lastBlock.height + 1

        val currentLIB = blockService.getLIBForSpace(spaceId)

        val propertyContext = PropertyContext(propertyService, contractService)

        evaluateBlocks(currentLIB, lastBlock, propertyContext)

        propertyContext.properties.forEach { log.info("Evaluated property $it") }

        val txResults = getTransactionResultsForNextBlock(spaceId, propertyContext)

        txResults
            .map { it.invocationResult }
            .forEach { log.info("Calculated transaction result $it") }

        val transactions = txResults.map { it.transaction }

        val votes = getVotesForBlock(lastBlock.hash)
        val prevVotes = getVotesForBlock(lastBlock.parentHash)

        val diff = votes.minus(prevVotes).size
        val weight = lastBlock.weight + votes.size

        val newBlock = blockService.newBlock(
            spaceId = spaceId,
            height = newHeight,
            weight = weight,
            diff = diff,
            timestamp = Instant.now().toEpochMilli(),
            parentHash = lastBlock.hash,
            producerId = producer.accountId,
            passphrase = passphrase,
            txHash = CryptoUtils.hashTransactions(transactions),
            validatorTxHash = CryptoUtils.hashVotes(votes),
            round = round,
            transactions = transactions,
            votes = votes
        )

        saveTxOutputs(txResults, newBlock)

        val blockData = BlockData(saveBlock(newBlock))

        moveLIBIfNeeded(currentLIB)

        return blockData
    }

    // TODO validate block

    private fun moveLIBIfNeeded(currentLIB: Block) {

        val newLIB = blockService.getLIBForSpace(currentLIB.spaceId)

        if (newLIB == currentLIB) {
            return
        }

        // TODO bug
        //require(newLIB.height > currentLIB.height)

        log.info(
            """
                Moving LIB from height ${currentLIB.height} to ${newLIB.height}.
                currentLIB.hash = ${currentLIB.hash}, newLIB.hash = ${newLIB.hash}.""".trimIndent()
        )

        // Apply transaction outputs if LIB moved forward
        // Iterate and apply all transactions from the block next to the previous LIB including NEW_LIB
        // In some situations LIB.height + 1 = NEW_LIB.height

        getBlocksRange(currentLIB, newLIB).forEach { block ->
            block.transactions.forEach { tx ->

                try {
                    val txOutput = transactionOutputRepository
                        .findById(TransactionOutputId(block.hash, tx.hash))
                        .orElseThrow()

                    val properties = ObjectUtils.readProperties(txOutput.output)

                    properties.forEach { log.info("Applying property $it") }

                    propertyService.updateProperties(properties)
                } catch (e: Exception) {
                    val errorMessage = "Could not process transaction output tx: ${tx.hash}, block: ${block.hash}"
                    log.warn(errorMessage, e)
                    throw RuntimeException(errorMessage, e)
                }
            }
        }
    }

    private fun saveTxOutputs(txResults: List<TransactionResult>, block: Block) {
        txResults.forEach { txResult ->

            val output = ObjectUtils.writeValueAsString(
                Properties(txResult.invocationResult.output)
            )

            val txOutput = TransactionOutput(
                TransactionOutputId(block.hash, txResult.transaction.hash),
                output
            )
            transactionOutputRepository.save(txOutput)
        }
    }

    private fun getTransactionResultsForNextBlock(
        spaceId: String,
        propertyContext: PropertyContext
    ): List<TransactionResult> {
        val transactions = getPendingTransactions(spaceId)

        return transactions
            .map { tx ->

                val localPropertyContext = propertyContext.getLocalCopy()

                val result = transactionProcessor.processTransaction(tx, localPropertyContext)

                if (result.isOK()) {
                    propertyContext.merge(localPropertyContext)
                }

                TransactionResult(tx, result)
            }
            .filter { it.invocationResult.isOK() }
    }

    private fun getPendingTransactions(spaceId: String): List<Transaction> {
        return transactionService.getPendingTransactionsBySpace(spaceId, MAX_REFERENCED_BLOCK_DEPTH)
    }

    private fun getVotesForBlock(blockHash: String): List<Vote> {
        return voteRepository.findByBlockHash(blockHash)
    }
}