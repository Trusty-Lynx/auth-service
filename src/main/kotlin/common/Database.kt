package de.trustylynx.common

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.SQLException
import javax.sql.DataSource

class DbConfig(val url: String, val user: String, val password: String) {
    override fun toString(): String = "DbConfig(url=$url, user=$user)"
}

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

suspend fun DataSource.isReachable(): Boolean = withContext(Dispatchers.IO) {
    try {
        connection.use { it.isValid(2) }
    } catch (_: SQLException) {
        false
    }
}
