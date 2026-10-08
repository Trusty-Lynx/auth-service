package de.trustylynx.common

import io.ktor.server.application.createRouteScopedPlugin
import java.security.MessageDigest

class ServiceTokenAuthConfig {
    var expectedToken: String = ""
}

val ServiceTokenAuth = createRouteScopedPlugin("ServiceTokenAuth", ::ServiceTokenAuthConfig) {
    val expected = pluginConfig.expectedToken
    require(expected.isNotEmpty()) { "ServiceTokenAuth needs an expectedToken" }

    onCall { call ->
        val provided = call.request.headers[SERVICE_TOKEN_HEADER] ?: throw Forbidden()
        if (!constantTimeEquals(provided, expected)) throw Forbidden()
    }
}

private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(sha256(a.toByteArray()), sha256(b.toByteArray()))
