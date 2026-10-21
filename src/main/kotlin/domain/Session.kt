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

class PresentedToken(val token: String) {
    override fun toString() = "PresentedToken()"
}
