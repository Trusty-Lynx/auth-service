package de.trustylynx.validation

import de.trustylynx.common.BadRequest
import de.trustylynx.common.decodeBase64OrNull

private const val MAX_EMAIL_LENGTH = 254
private const val LOGIN_KEY_LENGTH = 32

private const val MAX_TOKEN_LENGTH = 512

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

internal fun validateEmail(raw: String): String {
    val email = raw.trim().lowercase()
    if (email.length > MAX_EMAIL_LENGTH || !EMAIL.matches(email)) throw BadRequest("invalid email")
    return email
}

internal fun validateBase64(value: String, length: Int): ByteArray {
    if (value.length != (length + 2) / 3 * 4) throw BadRequest("invalid base64 length, expected $length bytes")
    val bytes = value.decodeBase64OrNull() ?: throw BadRequest("invalid base64 encoding")
    if (bytes.size != length) throw BadRequest("decoded value must be $length bytes")
    return bytes
}

internal fun validateLoginKey(value: String): ByteArray = validateBase64(value, LOGIN_KEY_LENGTH)

internal fun validateTokenFormat(token: String): String? =
    token.takeIf { it.isNotBlank() && it.length <= MAX_TOKEN_LENGTH }
