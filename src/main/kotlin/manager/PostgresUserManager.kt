package de.trustylynx.manager

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.trustylynx.AlreadyExists
import de.trustylynx.AppJson
import de.trustylynx.model.KdfParams
import de.trustylynx.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

object PostgresUserManager : UserManager {

    val dataSource: DataSource = HikariDataSource(
        HikariConfig().apply {
            poolName = "auth-db"
            jdbcUrl = System.getenv("DB_URL")
            username = System.getenv("DB_USER")
            password = System.getenv("DB_PASSWORD")
            maximumPoolSize = 10
        }
    )

    override suspend fun crate(user: User) = db { connection ->
        try {
            connection.prepareStatement(
                """
                    INSERT INTO users (user_id, email, salt_login, kdf_login, salt_s_login, login_verifier)
                    VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, user.userId)
                statement.setString(2, user.email)
                statement.setBytes(3, user.saltLogin)
                statement.setString(4, AppJson.encodeToString(KdfParams.serializer(), user.kdfLogin))
                statement.setBytes(5, user.saltSLogin)
                statement.setBytes(6, user.loginVerifier)
                statement.executeUpdate()
            }
        } catch (e: SQLException) {
            if (e.sqlState == "23505") throw AlreadyExists()
            throw e
        }
        Unit
    }

    override suspend fun findByEmail(email: String): User? {
        TODO("Not yet implemented")
    }

    override suspend fun findById(userId: String): User? {
        TODO("Not yet implemented")
    }

    override suspend fun updateLoginCredentials(
        userId: String,
        saltLogin: ByteArray,
        kdfLogin: ByteArray,
        saltSLogin: ByteArray,
        loginVerifier: ByteArray
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun delete(userId: String) {
        TODO("Not yet implemented")
    }

    private suspend fun <T> db(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use(block)
    }
}