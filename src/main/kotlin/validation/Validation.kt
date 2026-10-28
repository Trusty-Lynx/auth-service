package de.trustylynx.validation

import de.trustylynx.api.LoginRequest
import de.trustylynx.api.RegisterRequest
import de.trustylynx.api.TotpSetupRequest
import de.trustylynx.api.VerifySessionRequest
import de.trustylynx.common.BadRequest
import de.trustylynx.common.Unauthorized
import de.trustylynx.common.decodeBase64OrNull
import de.trustylynx.domain.KdfParams
import de.trustylynx.domain.LoginAttempt
import de.trustylynx.domain.NewAccount
import de.trustylynx.domain.SALT_LOGIN_LENGTH
import de.trustylynx.domain.TotpSetup
import de.trustylynx.security.Totp
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall

// Prüft Requests und macht daraus Domain-Objekte. Formatfehler werden zu 400 mit einem `reason` aus API.md.

private const val MAX_EMAIL_LENGTH = 254
private const val LOGIN_KEY_LENGTH = 32
private const val MAX_TOKEN_LENGTH = 512

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val TOTP_CODE = Regex("^[0-9]{${Totp.DIGITS}}$")

fun validatePreloginEmail(email: String?): String =
    validateEmail(email ?: throw BadRequest("missing email"))

fun RegisterRequest.validate(): NewAccount {
    if (kdf != KdfParams.DEFAULT) throw BadRequest("unsupported kdf parameters")

    return NewAccount(
        email = validateEmail(email),
        saltLogin = validateBase64(salt, SALT_LOGIN_LENGTH),
        kdf = kdf,
        loginKey = validateBase64(loginKey, LOGIN_KEY_LENGTH),
        totp = totp.validate(),
    )
}

fun LoginRequest.validate(): LoginAttempt = LoginAttempt(
    email = validateEmail(email),
    loginKey = validateBase64(loginKey, LOGIN_KEY_LENGTH),
    totpCode = totp?.let(::validateTotpCode),
)

fun TotpSetupRequest.validate(): TotpSetup =
    TotpSetup(secret = validateBase64(secret, Totp.SECRET_LENGTH), code = validateTotpCode(code))

/** Der Login-Token im Body von `/internal/sessions/verify`. */
fun VerifySessionRequest.validate(): String = validToken(token) ?: throw BadRequest("invalid token")

/** Der Login-Token aus `Authorization: Bearer …`. Fehlt er oder ist er ungültig, gibt es 401. */
fun ApplicationCall.bearerToken(): String {
    val header = request.headers[HttpHeaders.Authorization]?.trim() ?: throw Unauthorized()
    val parts = header.split(' ', limit = 2) // "Bearer abc" -> ["Bearer", "abc"]
    if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) throw Unauthorized()
    return validToken(parts[1].trim()) ?: throw Unauthorized()
}

private fun validateEmail(raw: String): String {
    val email = raw.trim().lowercase()
    if (email.length > MAX_EMAIL_LENGTH || !EMAIL.matches(email)) throw BadRequest("invalid email")
    return email
}

private fun validateBase64(value: String, length: Int): ByteArray {
    // Base64 mit Padding: je angefangene 3 Bytes 4 Zeichen, also 24 Zeichen für 16 Bytes und 44 für 32.
    if (value.length != (length + 2) / 3 * 4) throw BadRequest("invalid base64 length, expected $length bytes")
    val bytes = value.decodeBase64OrNull() ?: throw BadRequest("invalid base64 encoding")
    if (bytes.size != length) throw BadRequest("decoded value must be $length bytes")
    return bytes
}

private fun validateTotpCode(code: String): String {
    if (!TOTP_CODE.matches(code)) throw BadRequest("totp code must be ${Totp.DIGITS} digits")
    return code
}

private fun validToken(token: String): String? = token.takeIf { it.isNotBlank() && it.length <= MAX_TOKEN_LENGTH }
