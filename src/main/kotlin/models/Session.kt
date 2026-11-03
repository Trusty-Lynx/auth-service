package de.trustylynx.models

import java.time.Instant

/** Eine Zeile der Tabelle `sessions`. Gespeichert wird nur der Hash des Login-Tokens. */
class Session(
    val sessionId: String,
    val userId: String,
    val tokenHash: ByteArray,
    val createdAt: Instant,
    val expiresAt: Instant,
)
