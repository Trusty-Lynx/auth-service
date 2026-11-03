package de.trustylynx.routes

import de.trustylynx.models.HealthResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

const val HEALTH_PATH = "/health"

fun Route.healthRoute(isHealthy: suspend () -> Boolean) {
    get(HEALTH_PATH) {
        if (isHealthy()) {
            call.respond(HealthResponse("ok"))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse("unavailable"))
        }
    }
}
