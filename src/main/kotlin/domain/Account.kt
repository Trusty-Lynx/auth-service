package de.trustylynx.domain

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

class NewAccount(
    val email: String,
    val saltLogin: ByteArray,
    val kdf: KdfParams,
    val loginKey: ByteArray,
) {
    override fun toString() = "NewAccount()"
}

class LoginAttempt(val email: String, val loginKey: ByteArray) {
    override fun toString() = "LoginAttempt()"
}

class PreloginParams(val saltLogin: ByteArray, val kdf: KdfParams)

class RegisteredAccount(val userId: String, val token: String) {
    override fun toString() = "RegisteredAccount(userId=$userId)"
}

class LoggedIn(val token: String, val userId: String) {
    override fun toString() = "LoggedIn(userId=$userId)"
}
