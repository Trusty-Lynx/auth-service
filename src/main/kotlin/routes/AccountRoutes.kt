package de.trustylynx.routes

import de.trustylynx.models.BadRequest
import de.trustylynx.models.KdfParams
import de.trustylynx.models.LOGIN_KEY_LENGTH
import de.trustylynx.models.LoginRequest
import de.trustylynx.models.RegisterRequest
import de.trustylynx.models.SALT_LOGIN_LENGTH
import de.trustylynx.models.TotpSetupRequest
import de.trustylynx.security.Totp
import de.trustylynx.services.AccountService
import de.trustylynx.services.SessionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Endpoints für den Client: Request lesen, Eingaben prüfen, Service aufrufen, Antwort schicken. */
fun Route.accountRoutes(accounts: AccountService, sessions: SessionService) {
    get("/prelogin") {
        val email = call.request.queryParameters["email"] ?: throw BadRequest("missing email")
        call.respond(accounts.prelogin(validateEmail(email)))
    }

    post("/register") {
        val request = call.receive<RegisterRequest>()
        if (request.kdf != KdfParams.DEFAULT) throw BadRequest("unsupported kdf parameters")

        val response = accounts.register(
            email = validateEmail(request.email),
            saltLogin = validateBase64(request.salt, SALT_LOGIN_LENGTH),
            kdf = request.kdf,
            loginKey = validateBase64(request.loginKey, LOGIN_KEY_LENGTH),
            totpSecret = validateBase64(request.totp.secret, Totp.SECRET_LENGTH),
            totpCode = validateTotpCode(request.totp.code),
        )
        call.respond(HttpStatusCode.Created, response)
    }

    post("/login") {
        val request = call.receive<LoginRequest>()

        val response = accounts.login(
            email = validateEmail(request.email),
            loginKey = validateBase64(request.loginKey, LOGIN_KEY_LENGTH),
            totpCode = request.totp?.let { validateTotpCode(it) },
        )
        call.respond(response)
    }

    post("/account/totp") {
        // Vor dem Body prüfen: ohne gültigen Login-Token gibt es immer 401, auch bei kaputtem Body.
        val session = sessions.verify(call.bearerToken())
        val request = call.receive<TotpSetupRequest>()

        accounts.enableTotp(
            userId = session.userId,
            totpSecret = validateBase64(request.secret, Totp.SECRET_LENGTH),
            totpCode = validateTotpCode(request.code),
        )
        call.respond(HttpStatusCode.NoContent)
    }

    post("/logout") {
        sessions.logout(call.bearerToken())
        call.respond(HttpStatusCode.NoContent)
    }
}
