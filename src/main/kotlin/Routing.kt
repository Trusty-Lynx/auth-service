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
        get("/test") {
            val dbUrl = System.getenv("DB_URL")
            val dbUser = System.getenv("DB_USER")
            val dbPassword = System.getenv("DB_PASSWORD")
            call.respondText("Test endpoint. DB_URL: $dbUrl, DB_USER: $dbUser, DB_PASSWORD: $dbPassword")
        }
    }
}