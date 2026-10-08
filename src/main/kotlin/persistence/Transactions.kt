package de.trustylynx.persistence

import de.trustylynx.common.inTransaction
import javax.sql.DataSource

interface Transactions {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

class PostgresTransactions(private val dataSource: DataSource) : Transactions {

    override suspend fun <T> inTransaction(block: suspend () -> T): T = dataSource.inTransaction(block)
}
