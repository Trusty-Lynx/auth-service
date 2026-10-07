package de.trustylynx.model.api

import kotlinx.serialization.Serializable

@Serializable
data class PepperHmacRequest (
    val purpose: String,
    val userId: String,
    val key: String
)

@Serializable
data class PepperHmacResponse (
    val h: String
)
