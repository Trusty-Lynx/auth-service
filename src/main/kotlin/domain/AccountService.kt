package de.trustylynx.domain

import de.trustylynx.clients.KeyServiceClient
import de.trustylynx.common.AlreadyExists
import de.trustylynx.common.Unauthorized
import de.trustylynx.common.hmacSha256
import de.trustylynx.common.secureRandomBytes
import de.trustylynx.persistence.Transactions
import de.trustylynx.persistence.UserRepository
import de.trustylynx.security.LoginRateLimiter
import de.trustylynx.security.VerifierHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class AccountService(
    private val users: UserRepository,
    private val sessions: SessionService,
    private val keys: KeyServiceClient,
    private val transactions: Transactions,
    private val rateLimiter: LoginRateLimiter,
    private val preloginSecret: ByteArray,
) {

    suspend fun prelogin(email: String): PreloginParams {
        val user = users.findByEmail(email)
        val fakeSalt = fakeSalt(email)
        return if (user != null) PreloginParams(user.saltLogin, user.kdfLogin) else PreloginParams(fakeSalt, KdfParams.DEFAULT)
    }

    suspend fun register(account: NewAccount): RegisteredAccount {
        if (users.findByEmail(account.email) != null) throw AlreadyExists()

        val userId = Uuid.random().toString()
        val saltSLogin = secureRandomBytes(VerifierHasher.SALT_LENGTH)
        val loginVerifier = computeVerifier(userId, account.loginKey, saltSLogin)
        keys.createUserKeys(userId)

        val user = User(
            userId = userId,
            email = account.email,
            saltLogin = account.saltLogin,
            kdfLogin = account.kdf,
            saltSLogin = saltSLogin,
            loginVerifier = loginVerifier,
        )
        return transactions.inTransaction {
            users.create(user)
            val token = sessions.issue(userId)
            RegisteredAccount(userId = userId, token = token)
        }
    }

    suspend fun login(attempt: LoginAttempt): LoggedIn {
        rateLimiter.checkAllowed(attempt.email)
        try {
            val user = authenticate(attempt)
            val token = sessions.issue(user.userId)
            rateLimiter.reset(attempt.email)
            return LoggedIn(token = token, userId = user.userId)
        } catch (e: Unauthorized) {
            rateLimiter.recordFailure(attempt.email)
            throw e
        }
    }

    private suspend fun authenticate(attempt: LoginAttempt): User {
        val user = users.findByEmail(attempt.email)
        val h = keys.pepperHmac(user?.userId ?: DUMMY_USER_ID, attempt.loginKey)
        val verifierMatches = withContext(Dispatchers.Default) {
            VerifierHasher.matches(h, user?.saltSLogin ?: DUMMY_SALT, user?.loginVerifier ?: DUMMY_VERIFIER)
        }
        if (user == null || !verifierMatches) throw Unauthorized()
        return user
    }

    private suspend fun computeVerifier(userId: String, loginKey: ByteArray, saltSLogin: ByteArray): ByteArray {
        val h = keys.pepperHmac(userId, loginKey)
        return withContext(Dispatchers.Default) { VerifierHasher.hash(h, saltSLogin) }
    }

    private fun fakeSalt(email: String): ByteArray =
        hmacSha256(preloginSecret, email.toByteArray(Charsets.UTF_8)).copyOf(SALT_LOGIN_LENGTH)

    private companion object {
        const val DUMMY_USER_ID = "00000000-0000-0000-0000-000000000000"
        val DUMMY_SALT = ByteArray(VerifierHasher.SALT_LENGTH)
        val DUMMY_VERIFIER = ByteArray(VerifierHasher.LENGTH)
    }
}
