package de.trustylynx.services

import de.trustylynx.models.Session
import de.trustylynx.models.Unauthorized
import de.trustylynx.repositories.SessionRepository
import de.trustylynx.security.secureRandomBytes
import de.trustylynx.security.sha256
import de.trustylynx.security.toBase64
import java.time.Clock
import java.time.Duration
import kotlin.uuid.Uuid

private const val TOKEN_LENGTH = 32

class SessionService(
    private val sessions: SessionRepository,
    private val clock: Clock,
    private val tokenTtl: Duration,
) {
    fun newToken(): String = secureRandomBytes(TOKEN_LENGTH).toBase64()

    // doesn't save anything yet, register() stores it together with the new user
    fun newSession(userId: String, token: String): Session {
        val now = clock.instant()
        return Session(
            sessionId = Uuid.random().toString(),
            userId = userId,
            tokenHash = hash(token),
            createdAt = now,
            expiresAt = now.plus(tokenTtl),
        )
    }

    suspend fun issue(userId: String): String {
        val token = newToken()
        sessions.create(newSession(userId, token))
        return token
    }

    suspend fun verify(token: String): Session {
        val session = sessions.findByTokenHash(hash(token)) ?: throw Unauthorized()
        if (!session.expiresAt.isAfter(clock.instant())) throw Unauthorized()
        return session
    }

    suspend fun logout(token: String) {
        val session = verify(token)
        if (!sessions.delete(session.sessionId)) throw Unauthorized()
    }

    private fun hash(token: String): ByteArray = sha256(token.toByteArray(Charsets.UTF_8))
}
