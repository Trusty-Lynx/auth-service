package de.trustylynx.domain

import de.trustylynx.common.Unauthorized
import de.trustylynx.common.secureRandomBytes
import de.trustylynx.common.sha256
import de.trustylynx.common.toBase64
import de.trustylynx.persistence.SessionRepository
import java.time.Clock
import java.time.Duration
import kotlin.uuid.Uuid

class SessionService(
    private val sessions: SessionRepository,
    private val clock: Clock,
    private val tokenTtl: Duration,
) {
    /** Würfelt einen neuen Login-Token und baut die Session dazu, speichert aber noch nichts. */
    fun newSession(userId: String): NewSession {
        val token = secureRandomBytes(TOKEN_LENGTH).toBase64()
        val now = clock.instant()
        val session = Session(
            sessionId = Uuid.random().toString(),
            userId = userId,
            tokenHash = hash(token),
            createdAt = now,
            expiresAt = now.plus(tokenTtl),
        )
        return NewSession(session, token)
    }

    /** Legt eine neue Session an und gibt ihren Login-Token zurück. */
    suspend fun issue(userId: String): String {
        val newSession = newSession(userId)
        sessions.create(newSession.session)
        return newSession.token
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

    private companion object {
        const val TOKEN_LENGTH = 32
    }
}
