package de.trustylynx.common

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

private val LOCAL_ORIGIN = Regex("""^http://(localhost|127\.0\.0\.1):\d+$""")
private val ORIGIN = Regex("""^https?://[a-z0-9.-]+(:\d+)?$""")

data class CorsConfig(
    val allowedOrigins: Set<String> = emptySet(),
    val allowNullOrigin: Boolean = false,
) {
    companion object {
        fun fromEnv() = CorsConfig(
            allowedOrigins = parseAllowedOrigins(env("CORS_ALLOWED_ORIGINS", "")),
            allowNullOrigin = parseFlag("CORS_ALLOW_NULL_ORIGIN", env("CORS_ALLOW_NULL_ORIGIN", "false")),
        )

        fun parseAllowedOrigins(value: String): Set<String> =
            value.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .onEach {
                    require(ORIGIN.matches(it)) {
                        "CORS_ALLOWED_ORIGINS: invalid origin '$it', expected http(s)://host[:port]"
                    }
                }
                .toSet()

        fun parseFlag(name: String, value: String): Boolean =
            value.toBooleanStrictOrNull() ?: error("$name must be true or false")
    }
}

fun Application.configureCors(config: CorsConfig) {
    install(CORS) {
        allowOrigins { origin ->
            LOCAL_ORIGIN.matches(origin) ||
                origin in config.allowedOrigins ||
                // `Origin: null` schicken nicht nur lokal geöffnete Dateien, sondern auch sandboxed iframes und
                // manche Redirects, also potenziell jede Website. Nur für die lokale Entwicklung, auf dem Server
                // bleibt CORS_ALLOW_NULL_ORIGIN false. Nie zusammen mit allowCredentials oder Cookies verwenden.
                (config.allowNullOrigin && origin == "null")
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.XRequestId)
        allowHeader(DEVICE_ID_HEADER)
        allowHeader("X-Timestamp")
        allowHeader("X-Device-Sig")
        exposeHeader(HttpHeaders.XRequestId)
        exposeHeader(HttpHeaders.RetryAfter)
    }
}
