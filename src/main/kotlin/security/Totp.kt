package de.trustylynx.security

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/** TOTP nach RFC 6238 mit den Werten, die jede Authenticator-App kann: HMAC-SHA1, 6 Ziffern, 30 s. */
object Totp {
    const val SECRET_LENGTH = 20
    const val DIGITS = 6
    val PERIOD: Duration = Duration.ofSeconds(30)

    /** Erlaubt sind der aktuelle Zeitschritt und je einer davor und danach, falls die Uhr des Handys abweicht. */
    private const val ALLOWED_DRIFT = 1L
    private const val MODULO = 1_000_000 // 10^DIGITS

    fun step(time: Instant): Long = Math.floorDiv(time.epochSecond, PERIOD.seconds)

    fun code(secret: ByteArray, step: Long): String {
        val hash = hmacSha1(secret, ByteBuffer.allocate(Long.SIZE_BYTES).putLong(step).array())
        // Dynamic Truncation aus RFC 4226: 4 Bytes ab dem Offset im letzten Halbbyte, oberstes Bit weg.
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % MODULO).toString().padStart(DIGITS, '0')
    }

    /**
     * Der Zeitschritt, zu dem [code] passt, oder `null`. Schritte bis einschließlich [lastUsedStep] zählen nicht,
     * damit jeder Code nur einmal gilt.
     */
    fun matchingStep(secret: ByteArray, code: String, now: Instant, lastUsedStep: Long?): Long? {
        val current = step(now)
        var match: Long? = null
        // Alle Kandidaten in konstanter Zeit vergleichen, ohne früh abzubrechen.
        for (step in current - ALLOWED_DRIFT..current + ALLOWED_DRIFT) {
            val matches = MessageDigest.isEqual(code(secret, step).toByteArray(), code.toByteArray())
            if (matches && match == null && (lastUsedStep == null || step > lastUsedStep)) match = step
        }
        return match
    }
}
