package de.trustylynx.clients

import de.trustylynx.SERVICE_TOKEN_HEADER
import de.trustylynx.models.UpstreamException
import de.trustylynx.security.decodeBase64OrNull
import de.trustylynx.security.toBase64
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

private const val HMAC_LENGTH = 32

/** Interface, damit die Tests ohne echten Key-Service laufen (`Fakes.kt`). */
interface KeyServiceClient {
    suspend fun pepperHmac(userId: String, loginKey: ByteArray): ByteArray

    suspend fun createUserKeys(userId: String)
}

/** [baseUrl] ohne `/` am Ende, z. B. `https://key.nexvent.de`. */
class HttpKeyServiceClient(
    private val http: HttpClient,
    private val baseUrl: String,
    private val serviceToken: String,
) : KeyServiceClient {

    override suspend fun pepperHmac(userId: String, loginKey: ByteArray): ByteArray {
        val response = http.post("$baseUrl/internal/pepper-hmac") {
            header(SERVICE_TOKEN_HEADER, serviceToken)
            contentType(ContentType.Application.Json)
            setBody(PepperHmacRequest(purpose = "login", userId = userId, key = loginKey.toBase64()))
        }
        if (!response.status.isSuccess()) {
            throw UpstreamException("key-service pepper-hmac answered ${response.status.value}")
        }

        val body = try {
            response.body<PepperHmacResponse>()
        } catch (e: CancellationException) {
            throw e // Request wurde abgebrochen, das ist kein Fehler des Key-Service
        } catch (e: Exception) {
            throw UpstreamException("key-service pepper-hmac: unreadable response body", e)
        }

        val h = body.h.decodeBase64OrNull()
        if (h == null || h.size != HMAC_LENGTH) {
            throw UpstreamException("key-service pepper-hmac: h must be $HMAC_LENGTH bytes, Base64")
        }
        return h
    }

    override suspend fun createUserKeys(userId: String) {
        val response = http.post("$baseUrl/internal/users/create") {
            header(SERVICE_TOKEN_HEADER, serviceToken)
            contentType(ContentType.Application.Json)
            setBody(CreateUserKeysRequest(userId = userId))
        }
        if (!response.status.isSuccess()) {
            throw UpstreamException("key-service create-user-keys answered ${response.status.value}")
        }
    }
}

@Serializable
private data class PepperHmacRequest(val purpose: String, val userId: String, val key: String)

@Serializable
private data class PepperHmacResponse(val h: String)

@Serializable
private data class CreateUserKeysRequest(val userId: String)
