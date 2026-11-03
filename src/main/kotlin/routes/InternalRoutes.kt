package de.trustylynx.routes

import de.trustylynx.SERVICE_TOKEN_HEADER
import de.trustylynx.models.BadRequest
import de.trustylynx.models.Forbidden
import de.trustylynx.models.VerifySessionRequest
import de.trustylynx.models.VerifySessionResponse
import de.trustylynx.security.sha256
import de.trustylynx.services.SessionService
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.security.MessageDigest

fun Route.internalRoutes(sessions: SessionService, gatewayToken: String) {
    post("/internal/sessions/verify") {
        // check the token before the body, so a broken body without a token still gets a 403
        call.requireServiceToken(gatewayToken)

        val request = call.receive<VerifySessionRequest>()
        if (!isValidToken(request.token)) throw BadRequest("invalid token")

        val session = sessions.verify(request.token)
        call.respond(
            VerifySessionResponse(
                userId = session.userId,
                sessionId = session.sessionId,
                expiresAt = session.expiresAt.epochSecond,
            ),
        )
    }
}

private fun ApplicationCall.requireServiceToken(expectedToken: String) {
    val provided = request.headers[SERVICE_TOKEN_HEADER] ?: throw Forbidden()
    // hash first so tokens of different lengths still compare in constant time
    val matches = MessageDigest.isEqual(sha256(provided.toByteArray()), sha256(expectedToken.toByteArray()))
    if (!matches) throw Forbidden()
}
