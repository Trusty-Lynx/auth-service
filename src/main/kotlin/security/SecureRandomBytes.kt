package de.trustylynx.security
import java.security.SecureRandom

object SecureRandomBytes {
    private val random = SecureRandom()

    fun next(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
}