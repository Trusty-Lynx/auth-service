package de.trustylynx.service.server

import de.trustylynx.AlreadyExists
import de.trustylynx.BadRequest
import de.trustylynx.manager.PostgresUserManager
import de.trustylynx.manager.UserManager
import de.trustylynx.model.api.RegisterRequest
import de.trustylynx.model.api.RegisterResponse
import de.trustylynx.model.User
import de.trustylynx.security.SecureRandomBytes
import de.trustylynx.security.Validation
import de.trustylynx.security.VerifierHasher
import de.trustylynx.service.client.KeyHttpService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

object AccountService {

    private val users: UserManager = PostgresUserManager

    suspend fun register(request: RegisterRequest, deviceIdHeader: String?, clientIp: String): RegisterResponse {
        val email = Validation.email(request.email)
        val saltLogin = Validation.base64(request.salt, SALT_LOGIN_LENGTH)
        Validation.kdf(request.kdf)
        val loginKey = Validation.base64(request.loginKey, LOGIN_KEY_LENGTH)
        val deviceId = Validation.deviceId(deviceIdHeader)
        if (request.device.id != deviceId) throw BadRequest()
        val deviceName = Validation.deviceName(request.device.name)
        val publicKey = Validation.publicKey(request.device.publicKey)

        if (users.findByEmail(email) != null) throw AlreadyExists()

        val userId = Uuid.random().toString()
        val saltSLogin = SecureRandomBytes.next(VerifierHasher.SALT_LENGTH)
        val loginVerifier = computeVerifier(userId, loginKey, saltSLogin)

        users.crate(
            User(
                userId = userId,
                email = email,
                saltLogin = saltLogin,
                kdfLogin = request.kdf,
                saltSLogin = saltSLogin,
                loginVerifier = loginVerifier,
            ),
        )

        return RegisterResponse(
            userId = userId,
            token = "1234"
        )
    }

    private suspend fun computeVerifier(userId: String, loginKey: ByteArray, saltSLogin: ByteArray): ByteArray {
        val h = KeyHttpService.pepperHmac(userId, loginKey)
        return withContext(Dispatchers.Default) { VerifierHasher.hash(h, saltSLogin) }
        return ByteArray(32)
    }

        const val SALT_LOGIN_LENGTH = 16
        const val LOGIN_KEY_LENGTH = 32
}