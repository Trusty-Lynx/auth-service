package de.trustylynx.common

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class DbConfig(
    val url: String,
    val user: String,
    val password: String,
    val poolName: String,
    val maximumPoolSize: Int = 10,
) {
    override fun toString(): String = "DbConfig(url=$url, user=$user, poolName=$poolName)"
}

fun createDataSource(config: DbConfig): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        poolName = config.poolName
        jdbcUrl = config.url
        username = config.user
        password = config.password
        maximumPoolSize = config.maximumPoolSize
        connectionTimeout = 5_000
    },
)

fun migrateDatabase(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).load().migrate()
}

private class TransactionConnection(val connection: Connection) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransactionConnection>
}

suspend fun <T> DataSource.withConnection(block: (Connection) -> T): T {
    val transaction = currentCoroutineContext()[TransactionConnection]
    return withContext(Dispatchers.IO) {
        if (transaction != null) block(transaction.connection) else connection.use(block)
    }
}

suspend fun DataSource.isReachable(): Boolean =
    try {
        withConnection { it.isValid(2) }
    } catch (_: SQLException) {
        false
    }

suspend fun <T> DataSource.inTransaction(block: suspend () -> T): T = withContext(Dispatchers.IO) {
    connection.use { connection ->
        connection.autoCommit = false
        try {
            val result = withContext(TransactionConnection(connection)) { block() }
            connection.commit()
            result
        } catch (e: Throwable) {
            try {
                connection.rollback()
            } catch (rollbackError: SQLException) {
                e.addSuppressed(rollbackError)
            }
            throw e
        }
    }
}
