package de.trustylynx.security

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TotpTest {

    // RFC 6238, Anhang B (SHA-1). Die RFC listet 8 Ziffern, 6 Ziffern sind die letzten 6 davon.
    private val rfcSecret = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @Test
    fun `codes match the test vectors from RFC 6238`() {
        val vectors = mapOf(
            59L to "287082",
            1111111109L to "081804",
            1111111111L to "050471",
            1234567890L to "005924",
            2000000000L to "279037",
            20000000000L to "353130",
        )
        for ((seconds, expected) in vectors) {
            assertEquals(expected, Totp.code(rfcSecret, Totp.step(Instant.ofEpochSecond(seconds))), "time $seconds")
        }
    }

    @Test
    fun `a code matches its own step and one step before and after`() {
        val now = Instant.ofEpochSecond(1111111109)
        val step = Totp.step(now)

        for (offset in -1L..1L) {
            assertEquals(step + offset, Totp.matchingStep(rfcSecret, Totp.code(rfcSecret, step + offset), now, null))
        }
        assertNull(Totp.matchingStep(rfcSecret, Totp.code(rfcSecret, step - 2), now, null))
        assertNull(Totp.matchingStep(rfcSecret, Totp.code(rfcSecret, step + 2), now, null))
    }

    @Test
    fun `a step up to the last used one does not match again`() {
        val now = Instant.ofEpochSecond(1111111109)
        val step = Totp.step(now)
        val code = Totp.code(rfcSecret, step)

        assertNull(Totp.matchingStep(rfcSecret, code, now, lastUsedStep = step))
        assertNull(Totp.matchingStep(rfcSecret, code, now, lastUsedStep = step + 1))
        assertEquals(step, Totp.matchingStep(rfcSecret, code, now, lastUsedStep = step - 1))
    }
}
