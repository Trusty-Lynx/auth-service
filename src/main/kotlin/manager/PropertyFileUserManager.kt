package de.trustylynx.manager

import de.trustylynx.model.RegisterResponse
import de.trustylynx.model.User
import kotlin.uuid.Uuid

object PropertyFileUserManager : UserManager {

    override fun logUserOn(email: String, password: String): RegisterResponse? {
        val user = User(
            Uuid.random().toString(),
            email,
            password
        )
        val registerResponse = RegisterResponse(
            user.uid
        )
        return registerResponse
    }

    override fun getUserFromToken(token: String): String? {
        TODO("Not yet implemented")
    }

    override fun logUserOff(email: String): Boolean {
        TODO("Not yet implemented")
    }

}