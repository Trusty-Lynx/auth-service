package de.trustylynx.clients

import de.trustylynx.common.SERVICE_TOKEN_HEADER
import de.trustylynx.common.UpstreamException
import de.trustylynx.common.bodyOrThrow
import de.trustylynx.common.decodeBase64OrNull
import de.trustylynx.common.serviceUrl
import de.trustylynx.common.toBase64
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

interface KeyServiceClient {
    suspend fun pepperHmac(userId: String, loginKey: ByteArray): ByteArray

    suspend fun createUserKeys(userId: String)
}

class HttpKeyServiceClient(
    private val http: HttpClient,
    private val baseUrl: String,
    private val serviceToken: String,
) : KeyServiceClient {

    override suspend fun pepperHmac(userId: String, loginKey: ByteArray): ByteArray {
        val response = http.post(serviceUrl(baseUrl, "internal", "pepper-hmac")) {
            header(SERVICE_TOKEN_HEADER, serviceToken)
            contentType(ContentType.Application.Json)
            setBody(PepperHmacRequest(purpose = "login", userId = userId, key = loginKey.toBase64()))
        }
        val body = response.bodyOrThrow<PepperHmacResponse>("key-service pepper-hmac")
        val h = body.h.decodeBase64OrNull() ?: throw UpstreamException("key-service pepper-hmac: invalid Base64")
        if (h.size != HMAC_LENGTH) throw UpstreamException("key-service pepper-hmac: expected $HMAC_LENGTH bytes")
        return h
    }

    override suspend fun createUserKeys(userId: String) {
        val response = http.post(serviceUrl(baseUrl, "internal", "users", "create")) {
            header(SERVICE_TOKEN_HEADER, serviceToken)
            contentType(ContentType.Application.Json)
            setBody(CreateUserKeysRequest(userId = userId))
        }
        if (!response.status.isSuccess()) throw UpstreamException("key-service create-user-keys answered ${response.status.value}")
    }

    private companion object {
        const val HMAC_LENGTH = 32
    }
}

@Serializable
private data class PepperHmacRequest(val purpose: String, val userId: String, val key: String)

@Serializable
private data class PepperHmacResponse(val h: String)

@Serializable
private data class CreateUserKeysRequest(val userId: String)
