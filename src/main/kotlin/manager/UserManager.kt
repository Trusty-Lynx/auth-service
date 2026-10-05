package de.trustylynx.manager

import de.trustylynx.model.RegisterResponse
import de.trustylynx.model.User

interface UserManager {

    fun logUserOn(email: String, password: String): RegisterResponse?
    fun getUserFromToken(token: String): String?
    fun logUserOff(email: String): Boolean

}