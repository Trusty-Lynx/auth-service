package de.trustylynx.domain

import de.trustylynx.clients.KeyServiceClient
import de.trustylynx.common.AlreadyExists
import de.trustylynx.common.secureRandomBytes
import de.trustylynx.persistence.UserRepository
import de.trustylynx.security.VerifierHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class AccountService(
    private val users: UserRepository,
    private val keys: KeyServiceClient,
) {

    suspend fun register(account: NewAccount): RegisteredAccount {
        if (users.findByEmail(account.email) != null) throw AlreadyExists()

        val userId = Uuid.random().toString()
        val saltSLogin = secureRandomBytes(VerifierHasher.SALT_LENGTH)
        val loginVerifier = computeVerifier(userId, account.loginKey, saltSLogin)

        users.create(
            User(
                userId = userId,
                email = account.email,
                saltLogin = account.saltLogin,
                kdfLogin = account.kdf,
                saltSLogin = saltSLogin,
                loginVerifier = loginVerifier,
            ),
        )
        // TODO: account.device speichern, dafür fehlt ein DeviceRepository.

        // TODO: echtes Session-Token ausstellen.
        keys.createUserKeys(userId)
        return RegisteredAccount(userId = userId, token = "1234")

    }

    private suspend fun computeVerifier(userId: String, loginKey: ByteArray, saltSLogin: ByteArray): ByteArray {
        val h = keys.pepperHmac(userId, loginKey)
        return withContext(Dispatchers.Default) { VerifierHasher.hash(h, saltSLogin) }
    }
}
