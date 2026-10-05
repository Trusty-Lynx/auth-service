package de.trustylynx.model

import kotlinx.serialization.Serializable

@Serializable
data class User (
    val uid: String,
    var email: String,
    var password: String,
)