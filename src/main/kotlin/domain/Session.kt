package de.trustylynx.domain

import java.time.Instant

class Session(
    val sessionId: String,
    val userId: String,
    val deviceId: String,
    val tokenHash: ByteArray,
    val createdAt: Instant,
    val expiresAt: Instant,
) {
    override fun toString() = "Session(sessionId=$sessionId, userId=$userId, deviceId=$deviceId)"
}

class PresentedToken(val token: String, val deviceId: String) {
    override fun toString() = "PresentedToken(deviceId=$deviceId)"
}
