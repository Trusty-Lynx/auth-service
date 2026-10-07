package de.trustylynx.api

import de.trustylynx.common.BadRequest
import de.trustylynx.common.validateBase64
import de.trustylynx.common.validateEmail
import de.trustylynx.domain.KdfParams
import de.trustylynx.domain.NewAccount
import de.trustylynx.domain.NewDevice
import org.bouncycastle.asn1.x9.ECNamedCurveTable

const val SALT_LOGIN_LENGTH = 16
const val LOGIN_KEY_LENGTH = 32
const val MAX_DEVICE_NAME_LENGTH = 100
const val P256_PUBLIC_KEY_LENGTH = 65

private val DEVICE_ID = Regex("^[A-Za-z0-9._-]{1,128}$")
private val P256_CURVE = ECNamedCurveTable.getByName("secp256r1").curve

/** Prüft den Request und liefert nur geprüfte Daten an die Domain. Wirft [BadRequest]. */
fun RegisterRequest.validate(deviceIdHeader: String?): NewAccount {
    val email = validateEmail(email)
    val saltLogin = validateBase64(salt, SALT_LOGIN_LENGTH)
    val kdf = validateKdf(kdf)
    val loginKey = validateBase64(loginKey, LOGIN_KEY_LENGTH)
    val deviceId = validateDeviceId(deviceIdHeader)
    if (device.id != deviceId) throw BadRequest("device id does not match X-Device-Id header")
    val deviceName = validateDeviceName(device.name)
    val publicKey = validatePublicKey(device.publicKey)

    return NewAccount(
        email = email,
        saltLogin = saltLogin,
        kdf = kdf,
        loginKey = loginKey,
        device = NewDevice(id = deviceId, name = deviceName, publicKey = publicKey),
    )
}

private fun validateDeviceId(deviceId: String?): String {
    if (deviceId == null || !DEVICE_ID.matches(deviceId)) throw BadRequest("missing or invalid device id")
    return deviceId
}

private fun validateKdf(kdf: KdfParams): KdfParams {
    if (kdf != KdfParams.DEFAULT) throw BadRequest("unsupported kdf parameters")
    return kdf
}

private fun validateDeviceName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_DEVICE_NAME_LENGTH) {
        throw BadRequest("device name must be 1-$MAX_DEVICE_NAME_LENGTH characters")
    }
    return trimmed
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
