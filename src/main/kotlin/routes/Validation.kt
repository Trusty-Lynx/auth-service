package de.trustylynx.routes

import de.trustylynx.models.BadRequest
import de.trustylynx.models.Unauthorized
import de.trustylynx.security.Totp
import de.trustylynx.security.decodeBase64OrNull
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall

// Prüft Eingaben aus dem Request. Formatfehler werden zu 400 mit einem `reason`.

private const val MAX_EMAIL_LENGTH = 254
private const val MAX_TOKEN_LENGTH = 512

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val TOTP_CODE = Regex("^[0-9]{${Totp.DIGITS}}$")

/** Gibt die E-Mail normalisiert zurück (ohne Leerzeichen außen, alles klein). */
fun validateEmail(raw: String): String {
    val email = raw.trim().lowercase()
    if (email.length > MAX_EMAIL_LENGTH || !EMAIL.matches(email)) throw BadRequest("invalid email")
    return email
}

/** Prüft ein Base64-Feld, das genau [length] Bytes enthalten muss, und gibt die Bytes zurück. */
fun validateBase64(value: String, length: Int): ByteArray {
    // Base64 mit Padding: je angefangene 3 Bytes 4 Zeichen, also 24 Zeichen für 16 Bytes und 44 für 32.
    if (value.length != (length + 2) / 3 * 4) throw BadRequest("invalid base64 length, expected $length bytes")
    val bytes = value.decodeBase64OrNull() ?: throw BadRequest("invalid base64 encoding")
    if (bytes.size != length) throw BadRequest("decoded value must be $length bytes")
    return bytes
}

fun validateTotpCode(code: String): String {
    if (!TOTP_CODE.matches(code)) throw BadRequest("totp code must be ${Totp.DIGITS} digits")
    return code
}

fun isValidToken(token: String): Boolean = token.isNotBlank() && token.length <= MAX_TOKEN_LENGTH

/** Der Login-Token aus `Authorization: Bearer …`. Fehlt er oder ist er ungültig, gibt es 401. */
fun ApplicationCall.bearerToken(): String {
    val header = request.headers[HttpHeaders.Authorization]?.trim() ?: throw Unauthorized()
    val parts = header.split(' ', limit = 2) // "Bearer abc" -> ["Bearer", "abc"]
    if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) throw Unauthorized()

    val token = parts[1].trim()
    if (!isValidToken(token)) throw Unauthorized()
    return token
}
