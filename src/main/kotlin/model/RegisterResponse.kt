package de.trustylynx.model

import kotlinx.serialization.Serializable

@Serializable
data class RegisterResponse(
    var uid: String,
)