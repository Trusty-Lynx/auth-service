package de.trustylynx.model

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    var email: String,
    var password: String
)

