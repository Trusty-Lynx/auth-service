package de.trustylynx.services

import de.trustylynx.clients.KeyServiceClient
import de.trustylynx.models.AlreadyExists
import de.trustylynx.models.BadRequest
import de.trustylynx.models.KdfParams
import de.trustylynx.models.LoginResponse
import de.trustylynx.models.PreloginResponse
import de.trustylynx.models.RegisterResponse
import de.trustylynx.models.SALT_LOGIN_LENGTH
import de.trustylynx.models.Unauthorized
import de.trustylynx.models.User
import de.trustylynx.repositories.UserRepository
import de.trustylynx.security.LoginRateLimiter
import de.trustylynx.security.Totp
import de.trustylynx.security.VerifierHasher
import de.trustylynx.security.hmacSha256
import de.trustylynx.security.secureRandomBytes
import de.trustylynx.security.toBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import kotlin.uuid.Uuid

private const val DUMMY_USER_ID = "00000000-0000-0000-0000-000000000000"
private val DUMMY_SALT = ByteArray(VerifierHasher.SALT_LENGTH)
private val DUMMY_VERIFIER = ByteArray(VerifierHasher.LENGTH)

class AccountService(
    private val users: UserRepository,
    private val sessions: SessionService,
    private val keys: KeyServiceClient,
    private val rateLimiter: LoginRateLimiter,
    private val preloginSecret: ByteArray,
    private val clock: Clock,
) {

    // unknown emails get a fake salt that's always the same per email, so you can't tell which accounts exist
    suspend fun prelogin(email: String): PreloginResponse {
        val user = users.findByEmail(email)
        val fakeSalt = hmacSha256(preloginSecret, email.toByteArray(Charsets.UTF_8)).copyOf(SALT_LOGIN_LENGTH)
        return if (user != null) {
            PreloginResponse(salt = user.saltLogin.toBase64(), kdf = user.kdfLogin)
        } else {
            PreloginResponse(salt = fakeSalt.toBase64(), kdf = KdfParams.DEFAULT)
        }
    }

    suspend fun register(
        email: String,
        saltLogin: ByteArray,
        kdf: KdfParams,
        loginKey: ByteArray,
        totpSecret: ByteArray,
        totpCode: String,
    ): RegisterResponse {
        val totpStep = firstTotpStep(totpSecret, totpCode)
        if (users.findByEmail(email) != null) throw AlreadyExists()

        val userId = Uuid.random().toString()
        val saltSLogin = secureRandomBytes(VerifierHasher.SALT_LENGTH)
        val user = User(
            userId = userId,
            email = email,
            saltLogin = saltLogin,
            kdfLogin = kdf,
            saltSLogin = saltSLogin,
            loginVerifier = loginVerifier(userId, loginKey, saltSLogin),
            totpSecret = totpSecret,
            // the code from the registration counts as used, it won't work for a login
            totpLastStep = totpStep,
        )
        keys.createUserKeys(userId)

        val token = sessions.newToken()
        users.create(user, sessions.newSession(userId, token))
        return RegisterResponse(userId = userId, token = token)
    }

    suspend fun login(email: String, loginKey: ByteArray, totpCode: String?): LoginResponse {
        rateLimiter.checkAllowed(email)
        try {
            val user = authenticate(email, loginKey)
            checkTotp(user, totpCode)
            val token = sessions.issue(user.userId)
            rateLimiter.reset(email)
            return LoginResponse(token = token, userId = user.userId, totpEnabled = user.totpSecret != null)
        } catch (e: Unauthorized) {
            rateLimiter.recordFailure(email)
            throw e
        }
    }

    suspend fun enableTotp(userId: String, totpSecret: ByteArray, totpCode: String) {
        val totpStep = firstTotpStep(totpSecret, totpCode)
        if (!users.enableTotp(userId, totpSecret, totpStep)) throw AlreadyExists()
    }

    private suspend fun authenticate(email: String, loginKey: ByteArray): User {
        val user = users.findByEmail(email)
        // keep going with dummy values for unknown emails so both cases take the same time
        val h = keys.pepperHmac(user?.userId ?: DUMMY_USER_ID, loginKey)
        val verifierMatches = withContext(Dispatchers.Default) {
            VerifierHasher.matches(h, user?.saltSLogin ?: DUMMY_SALT, user?.loginVerifier ?: DUMMY_VERIFIER)
        }
        if (user == null || !verifierMatches) throw Unauthorized()
        return user
    }

    private suspend fun checkTotp(user: User, code: String?) {
        val secret = user.totpSecret ?: return // accounts from before TOTP get in without a code
        if (code == null) throw Unauthorized()
        val step = Totp.matchingStep(secret, code, clock.instant(), user.totpLastStep) ?: throw Unauthorized()
        if (!users.markTotpStepUsed(user.userId, step)) throw Unauthorized()
    }

    private fun firstTotpStep(secret: ByteArray, code: String): Long =
        Totp.matchingStep(secret, code, clock.instant(), lastUsedStep = null)
            ?: throw BadRequest("invalid_totp")

    // verifier = argon2id(h, salt_s_login) with h = hmac(pepper_login, loginKey) from the key service
    private suspend fun loginVerifier(userId: String, loginKey: ByteArray, saltSLogin: ByteArray): ByteArray {
        val h = keys.pepperHmac(userId, loginKey)
        return withContext(Dispatchers.Default) { VerifierHasher.hash(h, saltSLogin) }
    }
}
