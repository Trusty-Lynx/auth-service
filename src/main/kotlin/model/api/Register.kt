package de.trustylynx.model.api

import de.trustylynx.model.DeviceInfo
import de.trustylynx.model.KdfParams
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val salt: String,
    val kdf: KdfParams,
    val loginKey: String,
    val device: DeviceInfo
)

@Serializable
data class RegisterResponse(
    val userId: String,
    val token: String
)

