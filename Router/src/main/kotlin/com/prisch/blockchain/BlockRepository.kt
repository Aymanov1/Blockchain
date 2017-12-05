package com.prisch.blockchain

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.prisch.util.Result
import com.prisch.util.Success
import org.springframework.stereotype.Repository

@Repository
class BlockRepository(
        private val transactionRepository: TransactionRepository,
        private val blockchainIndex: BlockchainIndex) {

    private final val blockchain = mutableListOf<JsonNode>()

    init {
        val genesisString = jacksonObjectMapper().writeValueAsString(GenesisBlock())
        blockchain.add(jacksonObjectMapper().readTree(genesisString))
    }

    @Synchronized
    fun addBlock(block: JsonNode): Result {
        // TODO: Add validations

        blockchain.add(block)
        blockchainIndex.processBlock(block)
        block.get(BlockField.TRANSACTIONS.nodeName).forEach {
            transactionRepository.removeTransaction(it.get(TransactionField.HASH.nodeName).asText())
        }

        return Success
    }

    @Synchronized
    fun getBlocks(): List<JsonNode> {
        return blockchain.toList()
    }
}