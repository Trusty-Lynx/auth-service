package de.trustylynx.domain

import java.time.Instant

class Session(
    val sessionId: String,
    val userId: String,
    val tokenHash: ByteArray,
    val createdAt: Instant,
    val expiresAt: Instant,
) {
    override fun toString() = "Session(sessionId=$sessionId, userId=$userId)"
}

/** Eine neue, noch nicht gespeicherte Session und ihr Login-Token. Gespeichert wird später nur der Hash des Tokens. */
class NewSession(val session: Session, val token: String) {
    override fun toString() = "NewSession($session)"
}
