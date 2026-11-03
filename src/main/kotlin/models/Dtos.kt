package de.trustylynx.models

import kotlinx.serialization.Serializable

// the field names are the API, don't rename them

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

@Serializable
data class HealthResponse(val status: String)

@Serializable
data class ErrorResponse(val error: String, val reason: String? = null)
