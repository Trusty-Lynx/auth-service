package de.trustylynx.security

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

// RFC 6238 with the settings every authenticator app supports
object Totp {
    const val SECRET_LENGTH = 20
    const val DIGITS = 6
    val PERIOD: Duration = Duration.ofSeconds(30)

    // the phone's clock can be a bit off, so we also accept one step before and after
    private const val ALLOWED_DRIFT = 1L
    private const val MODULO = 1_000_000

    fun step(time: Instant): Long = Math.floorDiv(time.epochSecond, PERIOD.seconds)

    fun code(secret: ByteArray, step: Long): String {
        val hash = hmacSha1(secret, ByteBuffer.allocate(Long.SIZE_BYTES).putLong(step).array())
        // dynamic truncation from RFC 4226: 4 bytes from the offset in the last nibble, minus the top bit
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % MODULO).toString().padStart(DIGITS, '0')
    }

    // steps up to lastUsedStep don't count, that's what makes every code single use
    fun matchingStep(secret: ByteArray, code: String, now: Instant, lastUsedStep: Long?): Long? {
        val current = step(now)
        var match: Long? = null
        // no early exit, otherwise the timing would give away which step matched
        for (step in current - ALLOWED_DRIFT..current + ALLOWED_DRIFT) {
            val matches = MessageDigest.isEqual(code(secret, step).toByteArray(), code.toByteArray())
            if (matches && match == null && (lastUsedStep == null || step > lastUsedStep)) match = step
        }
        return match
    }
}
