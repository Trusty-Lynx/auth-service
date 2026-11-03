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

/** Login-Sessions: Token ausstellen, prüfen und beenden. */
class SessionService(
    private val sessions: SessionRepository,
    private val clock: Clock,
    private val tokenTtl: Duration,
) {
    /** Würfelt einen neuen Login-Token. */
    fun newToken(): String = secureRandomBytes(TOKEN_LENGTH).toBase64()

    /** Baut die Session zu einem Token, speichert sie aber noch nicht. */
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

    /** Legt eine neue Session an und gibt ihren Login-Token zurück. */
    suspend fun issue(userId: String): String {
        val token = newToken()
        sessions.create(newSession(userId, token))
        return token
    }

    /** Die Session zum Token, wenn es sie gibt und sie nicht abgelaufen ist. Sonst 401. */
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
