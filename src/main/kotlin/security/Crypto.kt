package de.trustylynx.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val secureRandom = SecureRandom()

fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    secureRandom.nextBytes(bytes)
    return bytes
}

fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = hmac("HmacSHA256", key, data)

/** Nur für TOTP: RFC 6238 und die Authenticator-Apps rechnen mit SHA-1. */
fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray = hmac("HmacSHA1", key, data)

private fun hmac(algorithm: String, key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance(algorithm)
    mac.init(SecretKeySpec(key, algorithm))
    return mac.doFinal(data)
}

fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

fun String.decodeBase64OrNull(): ByteArray? = try {
    Base64.getDecoder().decode(this)
} catch (_: IllegalArgumentException) {
    null
}
