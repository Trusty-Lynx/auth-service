package de.trustylynx.validation

import de.trustylynx.api.LoginRequest
import de.trustylynx.api.RegisterRequest
import de.trustylynx.api.VerifySessionRequest
import de.trustylynx.common.BadRequest
import de.trustylynx.common.Unauthorized
import de.trustylynx.common.bearerToken
import de.trustylynx.domain.KdfParams
import de.trustylynx.domain.LoginAttempt
import de.trustylynx.domain.NewAccount
import de.trustylynx.domain.PresentedToken
import de.trustylynx.domain.SALT_LOGIN_LENGTH
import io.ktor.server.application.ApplicationCall

fun validatePreloginEmail(email: String?): String =
    validateEmail(email ?: throw BadRequest("missing email"))

fun RegisterRequest.validate(): NewAccount {
    if (kdf != KdfParams.DEFAULT) throw BadRequest("unsupported kdf parameters")

    return NewAccount(
        email = validateEmail(email),
        saltLogin = validateBase64(salt, SALT_LOGIN_LENGTH),
        kdf = kdf,
        loginKey = validateLoginKey(loginKey),
    )
}

fun LoginRequest.validate(): LoginAttempt =
    LoginAttempt(email = validateEmail(email), loginKey = validateLoginKey(loginKey))

fun ApplicationCall.presentedToken(): PresentedToken =
    PresentedToken(token = bearerToken()?.let(::validateTokenFormat) ?: throw Unauthorized())

fun VerifySessionRequest.validate(): PresentedToken =
    PresentedToken(token = validateTokenFormat(token) ?: throw BadRequest("invalid token"))
