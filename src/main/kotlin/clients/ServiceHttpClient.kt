package de.trustylynx.clients

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.callid.CallId
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createServiceHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 2_000
        requestTimeoutMillis = 10_000
    }
    install(CallId) { addToHeader(HttpHeaders.XRequestId) }
}
