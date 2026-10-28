package de.trustylynx.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.callid.CallId
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

const val SERVICE_TOKEN_HEADER = "X-Service-Token"

/** HTTP-Client für Aufrufe an andere Services. Gibt die Request-ID des laufenden Requests weiter. */
fun createServiceHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    install(ContentNegotiation) { json(ServiceJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 2_000
        requestTimeoutMillis = 10_000
    }
    install(CallId) { addToHeader(HttpHeaders.XRequestId) }
}
