package de.trustylynx.api

import de.trustylynx.domain.KdfParams
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val salt: String,
    val kdf: KdfParams,
    val loginKey: String,
    val device: DeviceDto,
)

@Serializable
data class RegisterResponse(
    val userId: String,
    val token: String,
)

@Serializable
data class DeviceDto(
    val id: String? = null,
    val name: String,
    val publicKey: String,
)


