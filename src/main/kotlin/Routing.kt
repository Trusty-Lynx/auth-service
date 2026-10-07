package de.trustylynx

import de.trustylynx.model.api.RegisterRequest
import de.trustylynx.service.server.AccountService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            call.respond(HttpStatusCode.Created, AccountService.register(request, call.deviceIdHeader(), call.clientIp()))
        }
        post("/login") {
            call.respondText("Login endpoint")
        }
        post("/logout") {
            call.respondText("Logout endpoint")
        }
        get("/prelogin") {
            call.respond("Prelogin endpoint")
        }
        get("/user/{uid}") {
            val uid = call.parameters["uid"]
            call.respondText("User endpoint for UID: $uid")
        }
        get("/test") {
            val dbUrl = System.getenv("DB_URL")
            val dbUser = System.getenv("DB_USER")
            val dbPassword = System.getenv("DB_PASSWORD")
            call.respondText("Test endpoint. DB_URL: $dbUrl, DB_USER: $dbUser, DB_PASSWORD: $dbPassword")
        }
    }
}

const val DEVICE_ID_HEADER = "X-Device-Id"

fun ApplicationCall.deviceIdHeader(): String? = request.headers[DEVICE_ID_HEADER]

// OFFEN: Hinter dem Gateway ist das die IP des Gateways. Dann die vom Gateway gesetzte Client-IP auswerten.
fun ApplicationCall.clientIp(): String = request.origin.remoteHost