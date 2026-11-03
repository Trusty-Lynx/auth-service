package de.trustylynx.repositories

import de.trustylynx.models.Session
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

/** Interface, damit die Tests eine Variante ohne Datenbank einsetzen können (`Fakes.kt`). */
interface SessionRepository {
    suspend fun create(session: Session)

    suspend fun findByTokenHash(tokenHash: ByteArray): Session?

    suspend fun delete(sessionId: String): Boolean
}

class PostgresSessionRepository(private val dataSource: DataSource) : SessionRepository {

    override suspend fun create(session: Session) {
        dataSource.withConnection { connection -> insertSession(connection, session) }
    }

    override suspend fun findByTokenHash(tokenHash: ByteArray): Session? = dataSource.withConnection { connection ->
        val sql = "SELECT session_id, user_id, token_hash, created_at, expires_at FROM sessions WHERE token_hash = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setBytes(1, tokenHash)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toSession() else null
            }
        }
    }

    override suspend fun delete(sessionId: String): Boolean = dataSource.withConnection { connection ->
        connection.prepareStatement("DELETE FROM sessions WHERE session_id = ?").use { statement ->
            statement.setString(1, sessionId)
            statement.executeUpdate() == 1
        }
    }

    private fun ResultSet.toSession() = Session(
        sessionId = getString("session_id"),
        userId = getString("user_id"),
        tokenHash = getBytes("token_hash"),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
        expiresAt = getObject("expires_at", OffsetDateTime::class.java).toInstant(),
    )
}

/** Fügt eine Session über [connection] ein. [PostgresUserRepository] nutzt das für die erste Session in seiner Transaktion. */
fun insertSession(connection: Connection, session: Session) {
    val sql = "INSERT INTO sessions (session_id, user_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)"
    connection.prepareStatement(sql).use { statement ->
        statement.setString(1, session.sessionId)
        statement.setString(2, session.userId)
        statement.setBytes(3, session.tokenHash)
        statement.setObject(4, session.createdAt.atOffset(ZoneOffset.UTC))
        statement.setObject(5, session.expiresAt.atOffset(ZoneOffset.UTC))
        statement.executeUpdate()
    }
}
