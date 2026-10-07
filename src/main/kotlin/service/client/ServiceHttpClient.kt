package de.trustylynx.service.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

internal val ServiceJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal fun serviceUrl(baseUrl: String, vararg segments: String): Url =
    URLBuilder(baseUrl.trimEnd('/')).appendPathSegments(*segments).build()