package de.trustylynx.clients

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.callid.CallId
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** HTTP-Client für Aufrufe an andere Services. Gibt die Request-ID des laufenden Requests weiter. */
fun createServiceHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    install(ContentNegotiation) {
        // Zusätzliche Felder in den Antworten anderer Services stören nicht.
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 2_000
        requestTimeoutMillis = 10_000
    }
    install(CallId) { addToHeader(HttpHeaders.XRequestId) }
}
