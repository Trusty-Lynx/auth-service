package de.trustylynx.api

import de.trustylynx.common.toBase64
import de.trustylynx.domain.AccountService
import de.trustylynx.domain.SessionService
import de.trustylynx.validation.presentedToken
import de.trustylynx.validation.validate
import de.trustylynx.validation.validatePreloginEmail
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.accountRoutes(accounts: AccountService, sessions: SessionService) {
    get("/prelogin") {
        val email = validatePreloginEmail(call.request.queryParameters["email"])
        val params = accounts.prelogin(email)
        call.respond(PreloginResponse(salt = params.saltLogin.toBase64(), kdf = params.kdf))
    }

    post("/register") {
        val account = call.receive<RegisterRequest>().validate()
        val registered = accounts.register(account)
        call.respond(HttpStatusCode.Created, RegisterResponse(userId = registered.userId, token = registered.token))
    }

    post("/login") {
        val attempt = call.receive<LoginRequest>().validate()
        val loggedIn = accounts.login(attempt)
        call.respond(LoginResponse(token = loggedIn.token, userId = loggedIn.userId))
    }

    post("/logout") {
        sessions.logout(call.presentedToken())
        call.respond(HttpStatusCode.NoContent)
    }
}
