package de.trustylynx.common

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

const val HEALTH_PATH = "/health"

@Serializable
data class HealthResponse(val status: String)

fun Route.healthRoute(isHealthy: suspend () -> Boolean) {
    get(HEALTH_PATH) {
        if (isHealthy()) {
            call.respond(HealthResponse("ok"))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse("unavailable"))
        }
    }
}
