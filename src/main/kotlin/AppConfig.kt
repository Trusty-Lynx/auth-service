package de.trustylynx

import de.trustylynx.common.DbConfig
import de.trustylynx.common.env
import de.trustylynx.common.envOrDev

/** Alle Umgebungsvariablen des auth-service an einer Stelle. */
class AppConfig(
    val db: DbConfig,
    val keyServiceUrl: String,
) {
    companion object {
        fun fromEnv() = AppConfig(
            db = DbConfig(
                url = envOrDev("DB_URL", "jdbc:postgresql://localhost:5432/auth"),
                user = envOrDev("DB_USER", "dev"),
                password = envOrDev("DB_PASSWORD", "dev"),
                poolName = "auth-db",
            ),
            keyServiceUrl = env("KEY_SERVICE_URL", "https://key.nexvent.de"),
        )
    }
}
