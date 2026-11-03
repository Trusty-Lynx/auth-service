package de.trustylynx.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

private const val FRONTEND_ORIGIN = "https://trustylynx.nexvent.de"
private val LOCAL_ORIGIN = Regex("""^http://(localhost|127\.0\.0\.1):\d+$""")
private val ORIGIN = Regex("""^https?://[a-z0-9.-]+(:\d+)?$""")

data class CorsConfig(
    val allowedOrigins: Set<String> = emptySet(),
    val allowNullOrigin: Boolean = false,
) {
    companion object {
        fun parseAllowedOrigins(value: String): Set<String> {
            val origins = value.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            for (origin in origins) {
                require(ORIGIN.matches(origin)) {
                    "CORS_ALLOWED_ORIGINS: invalid origin '$origin', expected http(s)://host[:port]"
                }
            }
            return origins.toSet()
        }

        fun parseFlag(name: String, value: String): Boolean =
            value.toBooleanStrictOrNull() ?: error("$name must be true or false")
    }
}

fun Application.configureCors(config: CorsConfig) {
    install(CORS) {
        allowOrigins { origin ->
            LOCAL_ORIGIN.matches(origin) ||
                origin == FRONTEND_ORIGIN ||
                origin in config.allowedOrigins ||
                // "null" also comes from sandboxed iframes and some redirects, so pretty much any website can send it.
                // local dev only: keep it off on the server and never combine it with cookies or credentials
                (config.allowNullOrigin && origin == "null")
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.XRequestId)
        exposeHeader(HttpHeaders.XRequestId)
        exposeHeader(HttpHeaders.RetryAfter)
    }
}
