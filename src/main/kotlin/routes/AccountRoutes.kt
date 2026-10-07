package de.trustylynx.routes

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.accountRoutes() {
    get("/prelogin") {
        //TODO: Implement prelogin logic
    }
    post("/register") {
        //TODO: Implement registration logic
    }
    post("/login") {
        //TODO: Implement login logic
    }
}