package de.trustylynx.security

import de.trustylynx.BadRequest
import de.trustylynx.model.KdfParams
import org.bouncycastle.asn1.x9.ECNamedCurveTable
import java.util.Base64

object Validation {

    const val MAX_EMAIL_LENGTH = 254
    const val MAX_DEVICE_NAME_LENGTH = 100
    const val P256_PUBLIC_KEY_LENGTH = 65

    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val DEVICE_ID = Regex("^[A-Za-z0-9._-]{1,128}$")
    private val P256_CURVE = ECNamedCurveTable.getByName("secp256r1").curve

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun email(raw: String): String {
        val email = normalizeEmail(raw)
        if (email.length > MAX_EMAIL_LENGTH || !EMAIL.matches(email)) throw BadRequest()
        return email
    }

    fun base64(value: String, length: Int): ByteArray {
        if (value.length != (length +2)/3 * 4) throw BadRequest()
        val bytes = try {
            Base64.getDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            throw BadRequest()
        }
        if (bytes.size != length) throw BadRequest()
        return bytes
    }

    fun deviceId(deviceId: String?): String {
        if (deviceId == null || !DEVICE_ID.matches(deviceId)) throw BadRequest()
        return deviceId
    }

    fun kdf(kdf: KdfParams) {
        if (kdf != KdfParams.DEFAULT) throw BadRequest()
    }

    fun deviceName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_DEVICE_NAME_LENGTH) throw BadRequest()
        return trimmed
    }

    fun publicKey(value: String): ByteArray {
        val bytes = base64(value, P256_PUBLIC_KEY_LENGTH)
        if (bytes[0] != 0x04.toByte()) throw BadRequest()
        try {
            P256_CURVE.decodePoint(bytes)
        } catch (_: RuntimeException) {
            throw BadRequest()
        }
        return bytes
    }
}