package de.trustylynx.api

import de.trustylynx.common.requireServiceToken
import de.trustylynx.domain.SessionService
import de.trustylynx.validation.validate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/** Routen nur für andere Services. Jede Route muss zuerst den Service-Token prüfen. */
fun Route.internalRoutes(sessions: SessionService, gatewayToken: String) {
    require(gatewayToken.isNotEmpty()) { "gatewayToken must not be empty" }

    post("/internal/sessions/verify") {
        // Vor dem Body prüfen: ohne gültigen Service-Token gibt es immer 403, auch bei kaputtem Body.
        call.requireServiceToken(gatewayToken)

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
