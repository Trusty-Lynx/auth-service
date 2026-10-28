package de.trustylynx.api

import de.trustylynx.common.toBase64
import de.trustylynx.domain.AccountService
import de.trustylynx.domain.SessionService
import de.trustylynx.validation.bearerToken
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
        val loggedIn = accounts.register(account)
        call.respond(HttpStatusCode.Created, RegisterResponse(userId = loggedIn.userId, token = loggedIn.token))
    }

    post("/login") {
        val attempt = call.receive<LoginRequest>().validate()
        val loggedIn = accounts.login(attempt)
        call.respond(LoginResponse(token = loggedIn.token, userId = loggedIn.userId, totpEnabled = loggedIn.totpEnabled))
    }

    post("/account/totp") {
        // Vor dem Body prüfen: ohne gültigen Login-Token gibt es immer 401, auch bei kaputtem Body.
        val session = sessions.verify(call.bearerToken())
        accounts.enableTotp(session.userId, call.receive<TotpSetupRequest>().validate())
        call.respond(HttpStatusCode.NoContent)
    }

    post("/logout") {
        sessions.logout(call.bearerToken())
        call.respond(HttpStatusCode.NoContent)
    }
}
