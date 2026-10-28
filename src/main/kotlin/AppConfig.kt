package de.trustylynx

import de.trustylynx.common.CorsConfig
import de.trustylynx.common.DbConfig
import de.trustylynx.common.decodeBase64OrNull
import de.trustylynx.common.env
import de.trustylynx.common.envOrDev
import java.time.Duration

/** Alle Umgebungsvariablen an einer Stelle. Welche es gibt, steht in `.env.example`. */
class AppConfig(
    val db: DbConfig,
    val keyServiceUrl: String,
    val serviceTokenAuth: String,
    val serviceTokenGateway: String,
    val preloginSecret: ByteArray,
    val loginTokenTtl: Duration,
    val cors: CorsConfig,
) {
    // Ohne Tokens und Secret, damit davon nichts im Log landet.
    override fun toString(): String =
        "AppConfig(db=$db, keyServiceUrl=$keyServiceUrl, loginTokenTtl=$loginTokenTtl, cors=$cors)"

    companion object {
        private const val PRELOGIN_SECRET_LENGTH = 32

        fun fromEnv() = AppConfig(
            db = DbConfig(
                url = envOrDev("DB_URL", "jdbc:postgresql://localhost:5432/auth"),
                user = envOrDev("DB_USER", "dev"),
                password = envOrDev("DB_PASSWORD", "dev"),
            ),
            keyServiceUrl = env("KEY_SERVICE_URL", "https://key.nexvent.de").trimEnd('/'),
            serviceTokenAuth = envOrDev("SERVICE_TOKEN_AUTH", "rtvSCa1Fa&YhXXp8ffija%FytSmHCi=A"),
            serviceTokenGateway = envOrDev("SERVICE_TOKEN_GATEWAY", "dev-service-token-gateway"),
            preloginSecret = preloginSecret(envOrDev("PRELOGIN_SECRET", "ZGV2LXByZWxvZ2luLXNlY3JldC0zMi1ieXRlcy0hISE=")),
            loginTokenTtl = loginTokenTtl(env("LOGIN_TOKEN_TTL_SECONDS", "43200")),
            cors = CorsConfig(
                allowedOrigins = CorsConfig.parseAllowedOrigins(env("CORS_ALLOWED_ORIGINS", "")),
                allowNullOrigin = CorsConfig.parseFlag("CORS_ALLOW_NULL_ORIGIN", env("CORS_ALLOW_NULL_ORIGIN", "false")),
            ),
        )

        private fun preloginSecret(base64: String): ByteArray {
            val secret = base64.decodeBase64OrNull()
            require(secret != null && secret.size == PRELOGIN_SECRET_LENGTH) {
                "PRELOGIN_SECRET must be $PRELOGIN_SECRET_LENGTH bytes, Base64"
            }
            return secret
        }

        private fun loginTokenTtl(seconds: String): Duration {
            val value = seconds.toLongOrNull()
            require(value != null && value > 0) { "LOGIN_TOKEN_TTL_SECONDS must be a positive number" }
            return Duration.ofSeconds(value)
        }
    }
}
