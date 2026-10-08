package de.trustylynx.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val SALT_LOGIN_LENGTH = 16

class User(
    val userId: String,
    val email: String,
    val saltLogin: ByteArray,
    val kdfLogin: KdfParams,
    val saltSLogin: ByteArray,
    val loginVerifier: ByteArray,
) {
    override fun toString() = "User(userId=$userId)"
}

@Serializable
data class KdfParams(val alg: String, val m: Int, val t: Int, val p: Int, val len: Int) {
    companion object {
        val DEFAULT = KdfParams(alg = "argon2id", m = 65536, t = 3, p = 1, len = 32)
    }
}

@Serializable
enum class DeviceStatus {
    @SerialName("trusted") TRUSTED,
    @SerialName("unknown") UNKNOWN,
    @SerialName("blocked") BLOCKED,
}

class NewAccount(
    val email: String,
    val saltLogin: ByteArray,
    val kdf: KdfParams,
    val loginKey: ByteArray,
    val device: NewDevice,
) {
    override fun toString() = "NewAccount(device=$device)"
}

class NewDevice(val id: String, val name: String, val publicKey: ByteArray) {
    override fun toString() = "NewDevice(id=$id)"
}

class LoginAttempt(
    val email: String,
    val loginKey: ByteArray,
    val deviceId: String,
    val newDevice: NewDevice?,
) {
    override fun toString() = "LoginAttempt(deviceId=$deviceId)"
}

class PreloginParams(val saltLogin: ByteArray, val kdf: KdfParams)

class RegisteredAccount(val userId: String, val token: String) {
    override fun toString() = "RegisteredAccount(userId=$userId)"
}

class LoggedIn(val token: String, val userId: String, val deviceStatus: DeviceStatus) {
    override fun toString() = "LoggedIn(userId=$userId, deviceStatus=$deviceStatus)"
}
