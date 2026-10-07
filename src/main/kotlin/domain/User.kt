package de.trustylynx.domain

/**
 * Keine data class: equals() würde ByteArrays nur per Referenz vergleichen.
 * Nicht @Serializable, damit Salts und Verifier nie in einer Response landen.
 */
class User(
    val userId: String,
    val email: String,
    val saltLogin: ByteArray,
    val kdfLogin: KdfParams,
    val saltSLogin: ByteArray,
    val loginVerifier: ByteArray,
) {
    override fun toString(): String = "User(userId=$userId)"
}
