package de.trustylynx.clients

interface KeyServiceClient {

    suspend fun pepperHmac(userId: String, loginKey: ByteArray): ByteArray

    suspend fun createUserKeys(userId: String)

    suspend fun deleteUserKeys(userId: String)
}
