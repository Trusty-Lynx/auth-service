package de.trustylynx.manager

import de.trustylynx.model.User

interface UserManager {

    suspend fun crate(user: User)
    suspend fun findByEmail(email: String): User?
    suspend fun findById(userId: String): User?
    suspend fun updateLoginCredentials(
        userId: String,
        saltLogin: ByteArray,
        kdfLogin: ByteArray,
        saltSLogin: ByteArray,
        loginVerifier: ByteArray,
    )
    suspend fun delete(userId: String)

}