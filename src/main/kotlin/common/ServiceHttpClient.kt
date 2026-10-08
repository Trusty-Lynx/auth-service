package de.trustylynx.common

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.callid.CallId
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException

const val SERVICE_TOKEN_HEADER = "X-Service-Token"

fun createServiceHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    install(ContentNegotiation) { json(ServiceJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 2_000
        requestTimeoutMillis = 10_000
    }
    install(CallId) { addToHeader(HttpHeaders.XRequestId) }
}

fun serviceUrl(baseUrl: String, vararg segments: String): Url =
    URLBuilder(baseUrl.trimEnd('/')).appendPathSegments(*segments).build()

suspend inline fun <reified T> HttpResponse.bodyOrThrow(what: String): T {
    if (!status.isSuccess()) throw UpstreamException("$what answered ${status.value}")
    return try {
        body<T>()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw UpstreamException("$what: unreadable response body", e)
    }
}
