package de.trustylynx.plugins

import de.trustylynx.models.ApiException
import de.trustylynx.models.ErrorResponse
import de.trustylynx.models.TooManyRequests
import de.trustylynx.models.UpstreamException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.defaultExceptionStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond

/** Macht aus jeder Exception eine JSON-Antwort im Format von [ErrorResponse]. */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            if (cause is TooManyRequests) {
                call.response.header(HttpHeaders.RetryAfter, cause.retryAfterSeconds.toString())
            }
            call.respond(cause.status, ErrorResponse(cause.code, cause.reason))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "malformed request body"))
        }
        exception<UpstreamException> { call, cause ->
            call.application.log.error("Upstream error on ${call.request.httpMethod.value} ${call.request.path()}", cause)
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("upstream_error"))
        }
        exception<Throwable> { call, cause ->
            // Fehler, für die Ktor selbst einen Status kennt, z. B. 415 bei falschem Content-Type.
            val status = defaultExceptionStatusCode(cause)
            if (status != null) {
                call.respond(status, ErrorResponse(status.description.lowercase().replace(' ', '_')))
            } else {
                call.application.log.error("Unhandled error on ${call.request.httpMethod.value} ${call.request.path()}", cause)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error"))
            }
        }
    }
}
