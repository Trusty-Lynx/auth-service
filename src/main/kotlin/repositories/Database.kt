package de.trustylynx.repositories

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

class DbConfig(val url: String, val user: String, val password: String)

fun createDataSource(config: DbConfig): HikariDataSource {
    val hikari = HikariConfig()
    hikari.poolName = "auth-db"
    hikari.jdbcUrl = config.url
    hikari.username = config.user
    hikari.password = config.password
    hikari.connectionTimeout = 5_000
    return HikariDataSource(hikari)
}

fun migrateDatabase(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).load().migrate()
}

// jdbc blocks the thread while it waits, so this has to run on the IO pool
suspend fun <T> DataSource.withConnection(block: (Connection) -> T): T =
    withContext(Dispatchers.IO) { connection.use(block) }

suspend fun DataSource.isReachable(): Boolean = try {
    withConnection { it.isValid(2) }
} catch (_: SQLException) {
    false
}
