package de.trustylynx.model.api

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
    val reason: String
)
