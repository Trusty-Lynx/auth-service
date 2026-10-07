package de.trustylynx

import de.trustylynx.model.api.ErrorResponse
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequest> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.code, cause.reason))
        }
    }
}
