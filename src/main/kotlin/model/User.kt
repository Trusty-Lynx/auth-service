package de.trustylynx.model

import kotlinx.serialization.Serializable

@Serializable
data class User (
    val userId: String,
    var email: String,
    val saltLogin: ByteArray,
    val kdfLogin: KdfParams,
    val saltSLogin: ByteArray,
    val loginVerifier: ByteArray
) {
    override fun toString(): String = "User(userId=$userId)"
}