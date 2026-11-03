package de.trustylynx.models

import java.time.Instant

class Session(
    val sessionId: String,
    val userId: String,
    val tokenHash: ByteArray,
    val createdAt: Instant,
    val expiresAt: Instant,
)
