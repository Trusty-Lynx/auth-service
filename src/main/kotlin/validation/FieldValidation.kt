package de.trustylynx.validation

import de.trustylynx.common.BadRequest
import de.trustylynx.common.decodeBase64OrNull
import de.trustylynx.domain.NewDevice
import org.bouncycastle.asn1.x9.ECNamedCurveTable

private const val MAX_EMAIL_LENGTH = 254
private const val LOGIN_KEY_LENGTH = 32
private const val P256_PUBLIC_KEY_LENGTH = 65
private const val MAX_DEVICE_NAME_LENGTH = 100

private const val MAX_TOKEN_LENGTH = 512

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val DEVICE_ID = Regex("^[A-Za-z0-9._-]{1,128}$")
private val P256_CURVE = ECNamedCurveTable.getByName("secp256r1").curve

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

internal fun validateDeviceId(deviceId: String?): String {
    if (deviceId == null || !DEVICE_ID.matches(deviceId)) throw BadRequest("missing or invalid device id")
    return deviceId
}

internal fun validateDevice(id: String, name: String, publicKey: String): NewDevice {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty() || trimmedName.length > MAX_DEVICE_NAME_LENGTH) {
        throw BadRequest("device name must be 1-$MAX_DEVICE_NAME_LENGTH characters")
    }
    return NewDevice(id = id, name = trimmedName, publicKey = validatePublicKey(publicKey))
}

private fun validatePublicKey(value: String): ByteArray {
    val bytes = validateBase64(value, P256_PUBLIC_KEY_LENGTH)
    if (bytes[0] != 0x04.toByte()) throw BadRequest("public key must be an uncompressed P-256 point")
    try {
        P256_CURVE.decodePoint(bytes)
    } catch (_: RuntimeException) {
        throw BadRequest("public key is not a valid P-256 point")
    }
    return bytes
}

internal fun validateTokenFormat(token: String): String? =
    token.takeIf { it.isNotBlank() && it.length <= MAX_TOKEN_LENGTH }
