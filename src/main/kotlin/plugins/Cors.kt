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
        /** Liest `CORS_ALLOWED_ORIGINS`, z. B. `https://a.de, http://b.de:8080`. */
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
                // `Origin: null` schicken nicht nur lokal geöffnete Dateien, sondern auch sandboxed iframes und
                // manche Redirects, also potenziell jede Website. Nur für die lokale Entwicklung, auf dem Server
                // bleibt CORS_ALLOW_NULL_ORIGIN false. Nie zusammen mit allowCredentials oder Cookies verwenden.
                (config.allowNullOrigin && origin == "null")
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.XRequestId)
        exposeHeader(HttpHeaders.XRequestId)
        exposeHeader(HttpHeaders.RetryAfter)
    }
}
