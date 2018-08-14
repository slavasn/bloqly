package org.bloqly.machine.component

import org.bloqly.machine.Application
import org.bloqly.machine.Application.Companion.DEFAULT_SPACE
import org.bloqly.machine.model.Block
import org.bloqly.machine.model.Transaction
import org.bloqly.machine.repository.BlockRepository
import org.bloqly.machine.repository.TransactionOutputRepository
import org.bloqly.machine.service.AccountService
import org.bloqly.machine.test.BaseTest
import org.bloqly.machine.util.CryptoUtils
import org.bloqly.machine.util.decode16
import org.bloqly.machine.vo.BlockData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [Application::class])
class BlockProcessorTest : BaseTest() {

    @Autowired
    private lateinit var accountService: AccountService

    @Autowired
    private lateinit var transactionOutputRepository: TransactionOutputRepository

    @Autowired
    private lateinit var genesisService: GenesisService

    @Autowired
    private lateinit var blockRepository: BlockRepository

    private val blocks = mutableListOf<BlockData>()

    private lateinit var firstBlock: Block

    private val txs = arrayOfNulls<Transaction>(8)

    /**
     * In this test we send 1 amount to a user in each blocks and check the actual property values
     * are in tact with block finality logic
     */
    @Before
    override fun setup() {
        super.setup()

        firstBlock = blockService.getLIBForSpace(DEFAULT_SPACE)
        assertEquals(firstBlock.hash, getLIB().hash)
        assertEquals(0, firstBlock.height)
    }

    @Test
    fun testGetBlockRange() {
        populateBlocks(cleanup = false)

        val blocksRange = blockProcessor.getBlocksRange(
            blocks.first().block.toModel(),
            blocks.last().block.toModel()
        )

        val blockHashes = blocks.drop(1).map { it.block.hash }
        val rangeBlockHashes = blocksRange.map { it.hash }

        assertEquals(blockHashes, rangeBlockHashes)
    }

    @Test
    fun testVerifyBlock() {
        val blockData = blockProcessor.createNextBlock(DEFAULT_SPACE, validator(0), passphrase(0), 1)
        val producer = accountRepository.findByAccountId(blockData.block.producerId)!!

        val block = blockRepository.findByHash(blockData.block.hash)!!

        assertTrue(CryptoUtils.verifyBlock(block, producer.publicKey.decode16()))
    }

    @Test
    fun testBlockProcessed() {
        populateBlocks()

        assertNull(propertyService.findById(propertyId))

        blockProcessor.processReceivedBlock(blocks[0])
        assertNotNull(blockRepository.findByHash(blocks[0].block.hash))

        assertNull(propertyService.findById(propertyId))
    }

    @Test
    fun testProcessNewBlockWithOldVotes() {
        populateBlocks()

        val votes = blocks[0].votes.map { voteVO ->
            val account = accountService.getByPublicKey(voteVO.publicKey)
            voteVO.toModel(account)
        }

        voteRepository.saveAll(votes)

        blockProcessor.processReceivedBlock(blocks[0])
    }

    @Test
    fun testRejectsBlockWithTheSameHash() {
        populateBlocks()
        blockProcessor.processReceivedBlock(blocks[0])
        blockProcessor.processReceivedBlock(blocks[0])
    }

    @Test
    fun testInvalidTransactionNotIncluded() {

        testService.createTransaction()
        testService.createInvalidTransaction()

        val block = blockProcessor.createNextBlock(DEFAULT_SPACE, validator(0), passphrase(0), 1)

        assertEquals(1, block.transactions.size)
    }

    @Test
    fun testPropertyAppliedWhenLIBChanged() {
        populateBlocks()

        assertNull(propertyService.findById(propertyId))

        blockProcessor.processReceivedBlock(blocks[0])
        assertNull(propertyService.findById(propertyId))
        val block0 = blockService.loadBlockByHash(blocks[0].block.hash)
        assertEquals(1, block0.transactions.size)

        assertPropertyValueCandidate("1")
        assertNoPropertyValue()

        blockProcessor.processReceivedBlock(blocks[1])
        assertNull(propertyService.findById(propertyId))
        val block1 = blockService.loadBlockByHash(blocks[1].block.hash)
        assertEquals(1, block1.transactions.size)

        assertPropertyValueCandidate("2")
        assertNoPropertyValue()

        blockProcessor.processReceivedBlock(blocks[2])
        assertNull(propertyService.findById(propertyId))
        val block2 = blockService.loadBlockByHash(blocks[2].block.hash)
        assertEquals(1, block2.transactions.size)

        assertPropertyValueCandidate("3")
        assertNoPropertyValue()

        blockProcessor.processReceivedBlock(blocks[3])
        val block3 = blockService.loadBlockByHash(blocks[3].block.hash)
        assertEquals(1, block3.transactions.size)

        assertPropertyValueCandidate("4")
        assertPropertyValue("1")

        blockProcessor.processReceivedBlock(blocks[4])
        val block4 = blockService.loadBlockByHash(blocks[4].block.hash)
        assertEquals(1, block4.transactions.size)

        assertPropertyValueCandidate("5")
        assertPropertyValue("2")
    }

    private fun getLIB(): Block {
        return blockService.getLIBForSpace(DEFAULT_SPACE)
    }

    private fun populateBlocks(cleanup: Boolean = true) {
        txs[0] = testService.createTransaction()
        blocks.add(0, blockProcessor.createNextBlock(DEFAULT_SPACE, validator(0), passphrase(0), 1))
        assertEquals(firstBlock.hash, getLIB().hash)
        assertEquals(firstBlock.hash, txs[0]!!.referencedBlockHash)

        assertPropertyValueCandidate("1")
        assertNoPropertyValue()

        txs[1] = testService.createTransaction()
        blocks.add(1, blockProcessor.createNextBlock(DEFAULT_SPACE, validator(1), passphrase(1), 2))

        assertEquals(txs[1]!!.hash, blocks[1].transactions.first().hash)
        assertEquals(1, blocks[1].transactions.size)

        assertEquals(firstBlock.hash, getLIB().hash)
        assertEquals(firstBlock.hash, txs[1]!!.referencedBlockHash)

        assertPropertyValueCandidate("2")
        assertNoPropertyValue()

        txs[2] = testService.createTransaction()
        blocks.add(2, blockProcessor.createNextBlock(DEFAULT_SPACE, validator(2), passphrase(2), 3))

        assertEquals(txs[2]!!.hash, blocks[2].transactions.first().hash)
        assertEquals(1, blocks[2].transactions.size)

        assertEquals(firstBlock.hash, getLIB().hash)
        assertEquals(firstBlock.hash, txs[2]!!.referencedBlockHash)

        assertPropertyValueCandidate("3")
        assertNoPropertyValue()

        txs[3] = testService.createTransaction() // lib is first block yet
        blocks.add(3, blockProcessor.createNextBlock(DEFAULT_SPACE, validator(3), passphrase(3), 4))

        assertEquals(txs[3]!!.hash, blocks[3].transactions.first().hash)
        assertEquals(1, blocks[3].transactions.size)

        // lib changed, for the first time
        // all transactions from block[0] must be applied
        assertEquals(blocks[0].block.hash, getLIB().hash)
        assertEquals(firstBlock.hash, txs[3]!!.referencedBlockHash)

        assertPropertyValueCandidate("4")
        assertPropertyValue("1")

        txs[4] = testService.createTransaction()
        blocks.add(4, blockProcessor.createNextBlock(DEFAULT_SPACE, validator(0), passphrase(0), 5))

        assertEquals(txs[4]!!.hash, blocks[4].transactions.first().hash)
        assertEquals(1, blocks[4].transactions.size)

        assertEquals(blocks[1].block.hash, getLIB().hash)
        assertEquals(blocks[0].block.hash, txs[4]!!.referencedBlockHash)

        assertPropertyValueCandidate("5")
        assertPropertyValue("2")

        txs[5] = testService.createTransaction()
        blocks.add(5, blockProcessor.createNextBlock(DEFAULT_SPACE, validator(1), passphrase(1), 6))

        assertEquals(txs[5]!!.hash, blocks[5].transactions.first().hash)
        assertEquals(1, blocks[5].transactions.size)

        assertEquals(blocks[2].block.hash, getLIB().hash)
        assertEquals(blocks[1].block.hash, txs[5]!!.referencedBlockHash)

        assertPropertyValueCandidate("6")
        assertPropertyValue("3")

        txs[6] = testService.createTransaction()
        blocks.add(6, blockProcessor.createNextBlock(DEFAULT_SPACE, validator(2), passphrase(2), 7))

        assertEquals(txs[6]!!.hash, blocks[6].transactions.first().hash)
        assertEquals(1, blocks[6].transactions.size)

        assertEquals(blocks[3].block.hash, getLIB().hash)
        assertEquals(blocks[2].block.hash, txs[6]!!.referencedBlockHash)

        assertPropertyValueCandidate("7")
        assertPropertyValue("4")

        txs[7] = testService.createTransaction()
        blocks.add(7, blockProcessor.createNextBlock(DEFAULT_SPACE, validator(3), passphrase(3), 8))

        assertEquals(txs[7]!!.hash, blocks[7].transactions.first().hash)
        assertEquals(1, blocks[7].transactions.size)

        assertEquals(blocks[4].block.hash, getLIB().hash)
        assertEquals(blocks[3].block.hash, txs[7]!!.referencedBlockHash)

        assertPropertyValueCandidate("8")
        assertPropertyValue("5")

        if (cleanup) {
            val genesis = genesisService.exportFirst(DEFAULT_SPACE)
            testService.cleanup()
            testService.importAccounts()
            genesisService.importFirst(genesis)

            firstBlock = blockService.getLIBForSpace(DEFAULT_SPACE)
            assertEquals(0, firstBlock.height)
        }
    }
}