package de.trustylynx.service.client

import de.trustylynx.UpstreamException
import de.trustylynx.manager.KeyManager
import de.trustylynx.model.api.PepperHmacRequest
import de.trustylynx.model.api.PepperHmacResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.util.Base64

object KeyHttpService : KeyManager{

    const val HMAC_LENGTH = 32

    internal val ServiceJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val http: HttpClient = HttpClient(CIO.create()) {
        expectSuccess = false
        install(ContentNegotiation) { json(ServiceJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 2_000
            requestTimeoutMillis = 10_000
        }
    }

    private val baseUrl = "https://key.nexvent.de"

    fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeBase64(value: String, length: Int, what: String): ByteArray {
        val bytes = try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            throw UpstreamException("$what: invalid Base64")
        }
        if (bytes.size != length) throw UpstreamException("$what: expected $length bytes")
        return bytes
    }

    fun serviceUrl(baseUrl: String, vararg segments: String): Url =
        URLBuilder(baseUrl.trimEnd('/')).appendPathSegments(*segments).build()


    override suspend fun pepperHmac(userId: String, loginKey: ByteArray): ByteArray {
        val response = http.post(serviceUrl(baseUrl, "internal","pepper-hmac")) {
            //header(SERVICE_TOKEN_HEADER, serviceToken)
            contentType(ContentType.Application.Json)
            setBody(PepperHmacRequest(purpose = "login", userId = userId, key = encodeBase64(loginKey)))
        }
        if (response.status != HttpStatusCode.OK) {
            throw UpstreamException("key-servide pepper-hmac answered ${response.status.value}")
        }
        val body = response.parse<PepperHmacResponse>("key-service pepper-hmac")
        return decodeBase64(body.h, HMAC_LENGTH, "key-service pepper-hmac")
    }

    override suspend fun createUserKeys(userId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteUserKeys(userId: String) {
        TODO("Not yet implemented")
    }

    internal suspend inline fun <reified T> HttpResponse.parse(what: String): T = try {
        body<T>()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        throw UpstreamException("$what: unreadable response body")
    }
}