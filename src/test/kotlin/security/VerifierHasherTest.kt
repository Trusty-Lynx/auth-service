package de.trustylynx.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class VerifierHasherTest {

    @Test
    fun `login verifier matches test vector V0`() {
        val h = hex("3054ff7d44be22a77e1ecb2dbe2d4e90eeb0010bd0a964369ff32f20d82d1608")
        val saltSLogin = hex("5f993166912ab125bd6e783a930a4fd8")
        val expected = hex("23df2c77374ba443b56b1b560f0737ef284fa12cb42e07ff48624eca13340982")

        assertContentEquals(expected, VerifierHasher.hash(h, saltSLogin))
        assertTrue(VerifierHasher.matches(h, saltSLogin, expected))
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
