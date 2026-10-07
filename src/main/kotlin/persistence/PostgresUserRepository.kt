package de.trustylynx.persistence

import de.trustylynx.common.AlreadyExists
import de.trustylynx.common.AppJson
import de.trustylynx.common.withConnection
import de.trustylynx.domain.KdfParams
import de.trustylynx.domain.User
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

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

    override suspend fun findByEmail(email: String): User? = findOne(Column.EMAIL, email)

    override suspend fun findById(userId: String): User? = findOne(Column.USER_ID, userId)

    override suspend fun updateLoginCredentials(
        userId: String,
        saltLogin: ByteArray,
        kdfLogin: KdfParams,
        saltSLogin: ByteArray,
        loginVerifier: ByteArray,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun delete(userId: String) {
        TODO("Not yet implemented")
    }

    /** Nur feste Spaltennamen, damit kein Text von außen ins SQL gelangt. */
    private enum class Column(val sql: String) {
        USER_ID("user_id"),
        EMAIL("email"),
    }

    private suspend fun findOne(column: Column, value: String): User? = dataSource.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT user_id, email, salt_login, kdf_login, salt_s_login, login_verifier
            FROM users WHERE ${column.sql} = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, value)
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
