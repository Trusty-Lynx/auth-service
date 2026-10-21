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
    suspend fun issue(userId: String): String {
        val token = secureRandomBytes(TOKEN_LENGTH).toBase64()
        val now = clock.instant()
        sessions.create(
            Session(
                sessionId = Uuid.random().toString(),
                userId = userId,
                tokenHash = hash(token),
                createdAt = now,
                expiresAt = now.plus(tokenTtl),
            ),
        )
        return token
    }

    suspend fun verify(presented: PresentedToken): Session {
        val session = sessions.findByTokenHash(hash(presented.token)) ?: throw Unauthorized()
        if (!session.expiresAt.isAfter(clock.instant())) throw Unauthorized()
        return session
    }

    suspend fun logout(presented: PresentedToken) {
        val session = verify(presented)
        if (!sessions.delete(session.sessionId)) throw Unauthorized()
    }

    private fun hash(token: String): ByteArray = sha256(token.toByteArray(Charsets.UTF_8))

    private companion object {
        const val TOKEN_LENGTH = 32
    }
}
