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

/** Connection-Pool mit HikariCP (höchstens 10 Verbindungen, Hikari-Standard). */
fun createDataSource(config: DbConfig): HikariDataSource {
    val hikari = HikariConfig()
    hikari.poolName = "auth-db"
    hikari.jdbcUrl = config.url
    hikari.username = config.user
    hikari.password = config.password
    hikari.connectionTimeout = 5_000
    return HikariDataSource(hikari)
}

/** Spielt die SQL-Dateien aus `resources/db/migration` ein, die noch fehlen. */
fun migrateDatabase(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).load().migrate()
}

/** Holt eine Verbindung aus dem Pool. Läuft auf dem IO-Thread-Pool, weil JDBC beim Warten den Thread blockiert. */
suspend fun <T> DataSource.withConnection(block: (Connection) -> T): T =
    withContext(Dispatchers.IO) { connection.use(block) }

suspend fun DataSource.isReachable(): Boolean = try {
    withConnection { it.isValid(2) }
} catch (_: SQLException) {
    false
}
