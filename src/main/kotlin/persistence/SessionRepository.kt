package de.trustylynx.persistence

import de.trustylynx.common.withConnection
import de.trustylynx.domain.Session
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

interface SessionRepository {
    suspend fun upsert(session: Session)

    suspend fun findByTokenHash(tokenHash: ByteArray): Session?

    suspend fun delete(sessionId: String): Boolean
}

class PostgresSessionRepository(private val dataSource: DataSource) : SessionRepository {

    override suspend fun upsert(session: Session) {
        dataSource.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO sessions (session_id, user_id, device_id, token_hash, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, device_id) DO UPDATE SET
                    session_id = EXCLUDED.session_id,
                    token_hash = EXCLUDED.token_hash,
                    created_at = EXCLUDED.created_at,
                    expires_at = EXCLUDED.expires_at
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, session.sessionId)
                statement.setString(2, session.userId)
                statement.setString(3, session.deviceId)
                statement.setBytes(4, session.tokenHash)
                statement.setObject(5, session.createdAt.atOffset(ZoneOffset.UTC))
                statement.setObject(6, session.expiresAt.atOffset(ZoneOffset.UTC))
                statement.executeUpdate()
            }
        }
    }

    override suspend fun findByTokenHash(tokenHash: ByteArray): Session? = dataSource.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT session_id, user_id, device_id, token_hash, created_at, expires_at
            FROM sessions WHERE token_hash = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setBytes(1, tokenHash)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toSession() else null }
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
        deviceId = getString("device_id"),
        tokenHash = getBytes("token_hash"),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
        expiresAt = getObject("expires_at", OffsetDateTime::class.java).toInstant(),
    )
}
