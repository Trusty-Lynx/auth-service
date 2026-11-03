package de.trustylynx

import de.trustylynx.plugins.CorsConfig
import de.trustylynx.repositories.DbConfig
import de.trustylynx.security.decodeBase64OrNull
import java.time.Duration

const val SERVICE_TOKEN_HEADER = "X-Service-Token"

private const val PRELOGIN_SECRET_LENGTH = 32

private val isDevelopment = System.getProperty("io.ktor.development")?.toBoolean() == true

class AppConfig(
    val db: DbConfig,
    val keyServiceUrl: String,
    val serviceTokenAuth: String,
    val serviceTokenGateway: String,
    val preloginSecret: ByteArray,
    val loginTokenTtl: Duration,
    val cors: CorsConfig,
) {
    companion object {
        fun fromEnv() = AppConfig(
            db = DbConfig(
                url = envOrDev("DB_URL", "jdbc:postgresql://localhost:5432/auth"),
                user = envOrDev("DB_USER", "dev"),
                password = envOrDev("DB_PASSWORD", "dev"),
            ),
            keyServiceUrl = env("KEY_SERVICE_URL", "https://key.nexvent.de").trimEnd('/'),
            serviceTokenAuth = envOrDev("SERVICE_TOKEN_AUTH", "rtvSCa1Fa&YhXXp8ffija%FytSmHCi=A"),
            serviceTokenGateway = envOrDev("SERVICE_TOKEN_GATEWAY", "dev-service-token-gateway"),
            preloginSecret = parsePreloginSecret(envOrDev("PRELOGIN_SECRET", "ZGV2LXByZWxvZ2luLXNlY3JldC0zMi1ieXRlcy0hISE=")),
            loginTokenTtl = parseLoginTokenTtl(env("LOGIN_TOKEN_TTL_SECONDS", "43200")),
            cors = CorsConfig(
                allowedOrigins = CorsConfig.parseAllowedOrigins(env("CORS_ALLOWED_ORIGINS", "")),
                allowNullOrigin = CorsConfig.parseFlag("CORS_ALLOW_NULL_ORIGIN", env("CORS_ALLOW_NULL_ORIGIN", "false")),
            ),
        )
    }
}

private fun env(name: String, default: String): String {
    val value = System.getenv(name)
    return if (value.isNullOrBlank()) default else value
}

private fun envOrDev(name: String, devDefault: String): String {
    val value = System.getenv(name)
    if (!value.isNullOrBlank()) return value
    if (isDevelopment) return devDefault
    error("Missing required environment variable $name")
}

private fun parsePreloginSecret(base64: String): ByteArray {
    val secret = base64.decodeBase64OrNull()
    require(secret != null && secret.size == PRELOGIN_SECRET_LENGTH) {
        "PRELOGIN_SECRET must be $PRELOGIN_SECRET_LENGTH bytes, Base64"
    }
    return secret
}

private fun parseLoginTokenTtl(seconds: String): Duration {
    val value = seconds.toLongOrNull()
    require(value != null && value > 0) { "LOGIN_TOKEN_TTL_SECONDS must be a positive number" }
    return Duration.ofSeconds(value)
}
