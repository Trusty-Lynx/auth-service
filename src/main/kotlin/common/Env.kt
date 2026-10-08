package de.trustylynx.common

private val isDev: Boolean =
    System.getProperty("io.ktor.development")?.toBoolean() == true

fun env(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: error("Missing required environment variable $name")

fun env(name: String, default: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

fun envOrDev(name: String, devDefault: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: devDefault.takeIf { isDev }
        ?: error("Missing required environment variable $name")