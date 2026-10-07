package de.trustylynx.manager

interface KeyManager {

    suspend fun pepperHmac(userId: String, loginKey: ByteArray): ByteArray

    suspend fun createUserKeys(userId: String)

    suspend fun deleteUserKeys(userId: String)

}