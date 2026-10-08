package de.trustylynx.persistence

import de.trustylynx.common.AlreadyExists
import de.trustylynx.common.AppJson
import de.trustylynx.common.withConnection
import de.trustylynx.domain.KdfParams
import de.trustylynx.domain.User
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

interface UserRepository {
    suspend fun create(user: User)

    suspend fun findByEmail(email: String): User?
}

class PostgresUserRepository(private val dataSource: DataSource) : UserRepository {

    override suspend fun create(user: User) {
        dataSource.withConnection { connection ->
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO users (user_id, email, salt_login, kdf_login, salt_s_login, login_verifier)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
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
                if (e.sqlState == UNIQUE_VIOLATION) throw AlreadyExists()
                throw e
            }
        }
    }

    override suspend fun findByEmail(email: String): User? = dataSource.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT user_id, email, salt_login, kdf_login, salt_s_login, login_verifier
            FROM users WHERE email = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, email)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toUser() else null }
        }
    }

    private fun ResultSet.toUser() = User(
        userId = getString("user_id"),
        email = getString("email"),
        saltLogin = getBytes("salt_login"),
        kdfLogin = AppJson.decodeFromString(KdfParams.serializer(), getString("kdf_login")),
        saltSLogin = getBytes("salt_s_login"),
        loginVerifier = getBytes("login_verifier"),
    )

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
