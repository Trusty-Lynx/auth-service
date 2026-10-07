package de.trustylynx.api

import de.trustylynx.common.deviceIdHeader
import de.trustylynx.domain.AccountService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

// TODO: GET /prelogin, POST /login, POST /logout, GET /user/{uid} implementieren.
fun Route.accountRoutes(accounts: AccountService) {
    post("/register") {
        val account = call.receive<RegisterRequest>().validate(call.deviceIdHeader())
        val registered = accounts.register(account)
        call.respond(HttpStatusCode.Created, RegisterResponse(userId = registered.userId, token = registered.token))
    }
}
