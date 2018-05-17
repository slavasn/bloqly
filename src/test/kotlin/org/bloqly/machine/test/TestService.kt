package org.bloqly.machine.test

import org.bloqly.machine.Application
import org.bloqly.machine.Application.Companion.DEFAULT_SPACE
import org.bloqly.machine.component.EventProcessorService
import org.bloqly.machine.component.SerializationService
import org.bloqly.machine.model.Account
import org.bloqly.machine.model.TransactionType
import org.bloqly.machine.repository.AccountRepository
import org.bloqly.machine.repository.BlockRepository
import org.bloqly.machine.repository.ContractRepository
import org.bloqly.machine.repository.PropertyRepository
import org.bloqly.machine.repository.SpaceRepository
import org.bloqly.machine.service.AccountService
import org.bloqly.machine.service.TransactionService
import org.bloqly.machine.util.ParameterUtils.writeLong
import org.bloqly.machine.util.TestUtils
import org.bloqly.machine.util.TestUtils.TEST_BLOCK_BASE_DIR
import org.bloqly.machine.vo.TransactionVO
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.time.ZonedDateTime
import javax.annotation.PostConstruct
import javax.transaction.Transactional

@Component
@Transactional
class TestService(

    private val contractRepository: ContractRepository,
    private val propertyRepository: PropertyRepository,
    private val blockRepository: BlockRepository,
    private val spaceRepository: SpaceRepository,
    private val accountService: AccountService,
    private val eventProcessorService: EventProcessorService,
    private val transactionService: TransactionService,
    private val accountRepository: AccountRepository,
    private val serializationService: SerializationService

) {

    private lateinit var accounts: List<Account>

    @PostConstruct
    @Suppress("unused")
    fun init() {

        accounts = accountService.readAccounts(TEST_BLOCK_BASE_DIR)
    }

    fun cleanup() {
        contractRepository.deleteAll()
        propertyRepository.deleteAll()
        blockRepository.deleteAll()
        spaceRepository.deleteAll()
    }

    fun getRoot(): Account = accounts.first()

    fun getUser(): Account = accounts.last()

    fun getValidator(n: Int): Account = accounts[n]

    fun createBlockchain() {
        eventProcessorService.createBlockchain(Application.DEFAULT_SPACE, TEST_BLOCK_BASE_DIR)
    }

    fun newTransaction(): TransactionVO {

        val lastBlock = blockRepository.findFirstBySpaceOrderByHeightDesc(DEFAULT_SPACE)

        val root = accountRepository.findByPublicKey(getRoot().publicKey).orElseThrow()

        val user = accountRepository.findByPublicKey(getUser().publicKey).orElseThrow()

        val transaction = transactionService.newTransaction(

                space = DEFAULT_SPACE,

                origin = root,

                destination = user,

                value = writeLong("1"),

                transactionType = TransactionType.CALL,

                referencedBlockId = lastBlock.id,

                timestamp = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond()
        )

        return serializationService.transactionToVO(transaction)
    }
}