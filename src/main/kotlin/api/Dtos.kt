package de.trustylynx.api

import de.trustylynx.domain.KdfParams
import kotlinx.serialization.Serializable

@Serializable
data class PreloginResponse(val salt: String, val kdf: KdfParams)

@Serializable
data class RegisterRequest(
    val email: String,
    val salt: String,
    val kdf: KdfParams,
    val loginKey: String,
    val totp: TotpSetupRequest,
)

/** Body von `POST /account/totp` und Feld `totp` in `POST /register`. */
@Serializable
data class TotpSetupRequest(val secret: String, val code: String)

@Serializable
data class RegisterResponse(val userId: String, val token: String)

@Serializable
data class LoginRequest(val email: String, val loginKey: String, val totp: String? = null)

@Serializable
data class LoginResponse(val token: String, val userId: String, val totpEnabled: Boolean)

@Serializable
data class VerifySessionRequest(val token: String)

@Serializable
data class VerifySessionResponse(val userId: String, val sessionId: String, val expiresAt: Long)
