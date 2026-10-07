package de.trustylynx.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val id: String? = null,
    val name: String,
    val publicKey: String
)
