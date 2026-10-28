package de.trustylynx.persistence

import de.trustylynx.common.AlreadyExists
import de.trustylynx.common.AppJson
import de.trustylynx.domain.Session
import de.trustylynx.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import javax.sql.DataSource

interface UserRepository {
    /** Speichert den Nutzer zusammen mit seiner ersten Session. Entweder beides oder nichts. */
    suspend fun create(user: User, firstSession: Session)

    suspend fun findByEmail(email: String): User?

    /**
     * Merkt sich [step] als benutzten TOTP-Zeitschritt, aber nur, wenn er neuer ist als der bisherige.
     * `false`, wenn ein anderer Login denselben oder einen neueren Schritt schon benutzt hat.
     */
    suspend fun markTotpStepUsed(userId: String, step: Long): Boolean

    /** Speichert das TOTP-Secret eines Kontos, das noch keins hat. `false`, wenn es schon eins hat. */
    suspend fun enableTotp(userId: String, secret: ByteArray, step: Long): Boolean
}

class PostgresUserRepository(private val dataSource: DataSource) : UserRepository {

    override suspend fun create(user: User, firstSession: Session) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    insertUser(connection, user)
                    insertSession(connection, firstSession)
                    connection.commit()
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                }
            }
        }
    }

    override suspend fun findByEmail(email: String): User? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val sql = """
                SELECT user_id, email, salt_login, kdf_login, salt_s_login, login_verifier, totp_secret, totp_last_step
                FROM users WHERE email = ?
            """.trimIndent()
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, email)
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.toUser() else null
                }
            }
        }
    }

    override suspend fun markTotpStepUsed(userId: String, step: Long): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            // Ein einziges UPDATE mit Bedingung, damit zwei gleichzeitige Logins nicht beide durchkommen.
            val sql = """
                UPDATE users SET totp_last_step = ?
                WHERE user_id = ? AND totp_secret IS NOT NULL AND (totp_last_step IS NULL OR totp_last_step < ?)
            """.trimIndent()
            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, step)
                statement.setString(2, userId)
                statement.setLong(3, step)
                statement.executeUpdate() == 1
            }
        }
    }

    override suspend fun enableTotp(userId: String, secret: ByteArray, step: Long): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val sql = "UPDATE users SET totp_secret = ?, totp_last_step = ? WHERE user_id = ? AND totp_secret IS NULL"
            connection.prepareStatement(sql).use { statement ->
                statement.setBytes(1, secret)
                statement.setLong(2, step)
                statement.setString(3, userId)
                statement.executeUpdate() == 1
            }
        }
    }

    private fun insertUser(connection: Connection, user: User) {
        val sql = """
            INSERT INTO users (user_id, email, salt_login, kdf_login, salt_s_login, login_verifier, totp_secret, totp_last_step)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, user.userId)
            statement.setString(2, user.email)
            statement.setBytes(3, user.saltLogin)
            statement.setString(4, AppJson.encodeToString(user.kdfLogin))
            statement.setBytes(5, user.saltSLogin)
            statement.setBytes(6, user.loginVerifier)
            statement.setBytes(7, user.totpSecret)
            val totpLastStep = user.totpLastStep
            if (totpLastStep != null) statement.setLong(8, totpLastStep) else statement.setNull(8, Types.BIGINT)
            try {
                statement.executeUpdate()
            } catch (e: SQLException) {
                // Greift, wenn zwei Registrierungen mit derselben E-Mail gleichzeitig ankommen.
                if (e.sqlState == UNIQUE_VIOLATION) throw AlreadyExists()
                throw e
            }
        }
    }

    private fun ResultSet.toUser() = User(
        userId = getString("user_id"),
        email = getString("email"),
        saltLogin = getBytes("salt_login"),
        kdfLogin = AppJson.decodeFromString(getString("kdf_login")),
        saltSLogin = getBytes("salt_s_login"),
        loginVerifier = getBytes("login_verifier"),
        totpSecret = getBytes("totp_secret"),
        totpLastStep = getLong("totp_last_step").takeUnless { wasNull() },
    )

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
