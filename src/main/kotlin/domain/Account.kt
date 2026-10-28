package de.trustylynx.domain

import kotlinx.serialization.Serializable

// Klassen mit Geheimnissen (loginKey, Verifier, Token) überschreiben toString, damit davon nichts im Log landet.

const val SALT_LOGIN_LENGTH = 16

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
) {
    override fun toString() = "User(userId=$userId)"
}

@Serializable
data class KdfParams(val alg: String, val m: Int, val t: Int, val p: Int, val len: Int) {
    companion object {
        val DEFAULT = KdfParams(alg = "argon2id", m = 65536, t = 3, p = 1, len = 32)
    }
}

class PreloginParams(val saltLogin: ByteArray, val kdf: KdfParams)

/** Geprüfte Daten aus `POST /register`. */
class NewAccount(
    val email: String,
    val saltLogin: ByteArray,
    val kdf: KdfParams,
    val loginKey: ByteArray,
    val totp: TotpSetup,
) {
    override fun toString() = "NewAccount()"
}

/** Neues TOTP-Secret und der erste Code dazu, aus `POST /register` oder `POST /account/totp`. */
class TotpSetup(val secret: ByteArray, val code: String) {
    override fun toString() = "TotpSetup()"
}

/** Geprüfte Daten aus `POST /login`. [totpCode] fehlt, wenn der Client keinen Code mitschickt. */
class LoginAttempt(val email: String, val loginKey: ByteArray, val totpCode: String?) {
    override fun toString() = "LoginAttempt()"
}

/** Ergebnis von Registrierung und Login: der Nutzer und der Login-Token seiner neuen Session. */
class LoggedIn(val userId: String, val token: String, val totpEnabled: Boolean) {
    override fun toString() = "LoggedIn(userId=$userId)"
}
