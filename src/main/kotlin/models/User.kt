package de.trustylynx.models

import kotlinx.serialization.Serializable

const val SALT_LOGIN_LENGTH = 16
const val LOGIN_KEY_LENGTH = 32

// not a data class on purpose, toString() would print the secrets into the logs
class User(
    val userId: String,
    val email: String,
    val saltLogin: ByteArray,
    val kdfLogin: KdfParams,
    val saltSLogin: ByteArray,
    val loginVerifier: ByteArray,
    // null for accounts from before TOTP, until they set it up via POST /account/totp
    val totpSecret: ByteArray?,
    // codes up to this step can't be used again
    val totpLastStep: Long?,
)

@Serializable
data class KdfParams(val alg: String, val m: Int, val t: Int, val p: Int, val len: Int) {
    companion object {
        val DEFAULT = KdfParams(alg = "argon2id", m = 65536, t = 3, p = 1, len = 32)
    }
}
