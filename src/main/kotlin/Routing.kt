package de.trustylynx

import de.trustylynx.manager.PropertyFileUserManager
import de.trustylynx.model.RegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        post("/register") {
            val registerRequest = call.receive<RegisterRequest>()
            val registerResponse = PropertyFileUserManager.logUserOn(registerRequest.email, registerRequest.password)
            call.respond(HttpStatusCode.Created, registerResponse!!)
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
    }
}