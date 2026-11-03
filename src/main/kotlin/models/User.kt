package de.trustylynx.models

import kotlinx.serialization.Serializable

const val SALT_LOGIN_LENGTH = 16
const val LOGIN_KEY_LENGTH = 32

/**
 * Eine Zeile der Tabelle `users`.
 * Bewusst keine `data class`: So schreibt `toString()` keine Geheimnisse ins Log.
 */
class User(
    val userId: String,
    val email: String,
    val saltLogin: ByteArray,
    val kdfLogin: KdfParams,
    val saltSLogin: ByteArray,
    val loginVerifier: ByteArray,
    /** `null` bei Konten von vor TOTP, bis sie es über `POST /account/totp` einrichten. */
    val totpSecret: ByteArray?,
    /** Zuletzt benutzter TOTP-Zeitschritt. Codes bis zu diesem Schritt gelten nicht mehr. */
    val totpLastStep: Long?,
)

/** Argon2-Parameter, mit denen der Client aus dem Kontopasswort den `loginKey` ableitet. */
@Serializable
data class KdfParams(val alg: String, val m: Int, val t: Int, val p: Int, val len: Int) {
    companion object {
        val DEFAULT = KdfParams(alg = "argon2id", m = 65536, t = 3, p = 1, len = 32)
    }
}
