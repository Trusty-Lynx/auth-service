package de.trustylynx.api

import de.trustylynx.common.ServiceTokenAuth
import de.trustylynx.domain.SessionService
import de.trustylynx.validation.validate
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.internalRoutes(sessions: SessionService, gatewayToken: String) {
    route("/internal") {
        install(ServiceTokenAuth) { expectedToken = gatewayToken }

        post("/sessions/verify") {
            val session = sessions.verify(call.receive<VerifySessionRequest>().validate())
            call.respond(
                VerifySessionResponse(
                    userId = session.userId,
                    sessionId = session.sessionId,
                    expiresAt = session.expiresAt.epochSecond,
                ),
            )
        }
    }
}
