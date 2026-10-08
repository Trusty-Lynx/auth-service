package de.trustylynx.validation

import de.trustylynx.api.LoginRequest
import de.trustylynx.api.RegisterRequest
import de.trustylynx.api.VerifySessionRequest
import de.trustylynx.common.BadRequest
import de.trustylynx.common.Unauthorized
import de.trustylynx.common.bearerToken
import de.trustylynx.common.deviceIdHeader
import de.trustylynx.domain.KdfParams
import de.trustylynx.domain.LoginAttempt
import de.trustylynx.domain.NewAccount
import de.trustylynx.domain.PresentedToken
import de.trustylynx.domain.SALT_LOGIN_LENGTH
import io.ktor.server.application.ApplicationCall

fun validatePreloginEmail(email: String?): String =
    validateEmail(email ?: throw BadRequest("missing email"))

fun RegisterRequest.validate(deviceIdHeader: String?): NewAccount {
    if (kdf != KdfParams.DEFAULT) throw BadRequest("unsupported kdf parameters")
    val deviceId = validateDeviceId(deviceIdHeader)
    if (device.id != deviceId) throw BadRequest("device id does not match X-Device-Id header")

    return NewAccount(
        email = validateEmail(email),
        saltLogin = validateBase64(salt, SALT_LOGIN_LENGTH),
        kdf = kdf,
        loginKey = validateLoginKey(loginKey),
        device = validateDevice(deviceId, device.name, device.publicKey),
    )
}

fun LoginRequest.validate(deviceIdHeader: String?): LoginAttempt {
    val deviceId = validateDeviceId(deviceIdHeader)

    return LoginAttempt(
        email = validateEmail(email),
        loginKey = validateLoginKey(loginKey),
        deviceId = deviceId,
        newDevice = device?.let { validateDevice(deviceId, it.name, it.publicKey) },
    )
}

fun ApplicationCall.presentedToken(): PresentedToken {
    val token = bearerToken()?.let(::validateTokenFormat) ?: throw Unauthorized()
    val deviceId = deviceIdHeader() ?: throw Unauthorized()
    return PresentedToken(token = token, deviceId = deviceId)
}

fun VerifySessionRequest.validate(): PresentedToken =
    PresentedToken(
        token = validateTokenFormat(token) ?: throw BadRequest("invalid token"),
        deviceId = validateDeviceId(deviceId),
    )
