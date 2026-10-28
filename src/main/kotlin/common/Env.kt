package de.trustylynx.common

private val isDev: Boolean =
    System.getProperty("io.ktor.development")?.toBoolean() == true

/** Optionale Variable. Fehlt sie, gilt [default]. */
fun env(name: String, default: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

/** Pflichtvariable. Nur im Dev-Modus (`-Pdevelopment`) darf sie fehlen, dann gilt [devDefault]. */
fun envOrDev(name: String, devDefault: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: devDefault.takeIf { isDev }
        ?: error("Missing required environment variable $name")