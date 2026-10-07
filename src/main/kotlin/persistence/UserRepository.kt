package de.trustylynx.persistence

import de.trustylynx.domain.KdfParams
import de.trustylynx.domain.User

interface UserRepository {

    /** Wirft [de.trustylynx.common.AlreadyExists], wenn E-Mail oder ID schon vergeben sind. */
    suspend fun create(user: User)

    suspend fun findByEmail(email: String): User?

    suspend fun findById(userId: String): User?

    suspend fun updateLoginCredentials(
        userId: String,
        saltLogin: ByteArray,
        kdfLogin: KdfParams,
        saltSLogin: ByteArray,
        loginVerifier: ByteArray,
    )

    suspend fun delete(userId: String)
}
