package de.trustylynx.domain

import de.trustylynx.clients.KeyServiceClient
import de.trustylynx.common.AlreadyExists
import de.trustylynx.common.BadRequest
import de.trustylynx.common.Unauthorized
import de.trustylynx.common.hmacSha256
import de.trustylynx.common.secureRandomBytes
import de.trustylynx.persistence.UserRepository
import de.trustylynx.security.LoginRateLimiter
import de.trustylynx.security.Totp
import de.trustylynx.security.VerifierHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import kotlin.uuid.Uuid

class AccountService(
    private val users: UserRepository,
    private val sessions: SessionService,
    private val keys: KeyServiceClient,
    private val rateLimiter: LoginRateLimiter,
    private val preloginSecret: ByteArray,
    private val clock: Clock,
) {

    /** Salt und KDF-Parameter. Unbekannte E-Mails bekommen ein Fake-Salt, das pro E-Mail immer gleich ist. */
    suspend fun prelogin(email: String): PreloginParams {
        val user = users.findByEmail(email)
        val fakeSalt = fakeSalt(email)
        return if (user != null) PreloginParams(user.saltLogin, user.kdfLogin) else PreloginParams(fakeSalt, KdfParams.DEFAULT)
    }

    /** Legt das Konto mit TOTP, seine Schlüssel beim Key-Service und die erste Session an. */
    suspend fun register(account: NewAccount): LoggedIn {
        val totpStep = firstTotpStep(account.totp)
        if (users.findByEmail(account.email) != null) throw AlreadyExists()

        val userId = Uuid.random().toString()
        val saltSLogin = secureRandomBytes(VerifierHasher.SALT_LENGTH)
        val user = User(
            userId = userId,
            email = account.email,
            saltLogin = account.saltLogin,
            kdfLogin = account.kdf,
            saltSLogin = saltSLogin,
            loginVerifier = loginVerifier(userId, account.loginKey, saltSLogin),
            totpSecret = account.totp.secret,
            // Der Code aus der Registrierung zählt als benutzt und taugt nicht mehr für einen Login.
            totpLastStep = totpStep,
        )
        keys.createUserKeys(userId)

        val firstSession = sessions.newSession(userId)
        users.create(user, firstSession.session)
        return LoggedIn(userId = userId, token = firstSession.token, totpEnabled = true)
    }

    suspend fun login(attempt: LoginAttempt): LoggedIn {
        rateLimiter.checkAllowed(attempt.email)
        try {
            val user = authenticate(attempt)
            checkTotp(user, attempt.totpCode)
            val token = sessions.issue(user.userId)
            rateLimiter.reset(attempt.email)
            return LoggedIn(userId = user.userId, token = token, totpEnabled = user.totpSecret != null)
        } catch (e: Unauthorized) {
            rateLimiter.recordFailure(attempt.email)
            throw e
        }
    }

    private suspend fun authenticate(attempt: LoginAttempt): User {
        val user = users.findByEmail(attempt.email)
        // Bei unbekannter E-Mail mit Dummy-Werten weiterrechnen, damit beide Fälle gleich lange dauern.
        val h = keys.pepperHmac(user?.userId ?: DUMMY_USER_ID, attempt.loginKey)
        val verifierMatches = withContext(Dispatchers.Default) {
            VerifierHasher.matches(h, user?.saltSLogin ?: DUMMY_SALT, user?.loginVerifier ?: DUMMY_VERIFIER)
        }
        if (user == null || !verifierMatches) throw Unauthorized()
        return user
    }

    /** Richtet TOTP für ein Konto von vor TOTP ein. Hat das Konto schon TOTP, kommt `409`. */
    suspend fun enableTotp(userId: String, setup: TotpSetup) {
        val totpStep = firstTotpStep(setup)
        if (!users.enableTotp(userId, setup.secret, totpStep)) throw AlreadyExists()
    }

    /** Konten ohne TOTP-Secret kommen ohne Code rein. Alle anderen brauchen einen Code, der noch nicht benutzt wurde. */
    private suspend fun checkTotp(user: User, code: String?) {
        val secret = user.totpSecret ?: return
        if (code == null) throw Unauthorized()
        val step = Totp.matchingStep(secret, code, clock.instant(), user.totpLastStep) ?: throw Unauthorized()
        // Kommen zwei Logins mit demselben Code gleichzeitig an, gewinnt hier nur einer.
        if (!users.markTotpStepUsed(user.userId, step)) throw Unauthorized()
    }

    /** Prüft den ersten Code zu einem neuen Secret und gibt seinen Zeitschritt zurück. */
    private fun firstTotpStep(setup: TotpSetup): Long =
        Totp.matchingStep(setup.secret, setup.code, clock.instant(), lastUsedStep = null)
            ?: throw BadRequest(INVALID_TOTP)

    /** Login-Verifier = Argon2id(h, salt_s_login) mit h = HMAC(pepper_login, loginKey) vom Key-Service. */
    private suspend fun loginVerifier(userId: String, loginKey: ByteArray, saltSLogin: ByteArray): ByteArray {
        val h = keys.pepperHmac(userId, loginKey)
        return withContext(Dispatchers.Default) { VerifierHasher.hash(h, saltSLogin) }
    }

    private fun fakeSalt(email: String): ByteArray =
        hmacSha256(preloginSecret, email.toByteArray(Charsets.UTF_8)).copyOf(SALT_LOGIN_LENGTH)

    private companion object {
        const val INVALID_TOTP = "invalid_totp"
        const val DUMMY_USER_ID = "00000000-0000-0000-0000-000000000000"
        val DUMMY_SALT = ByteArray(VerifierHasher.SALT_LENGTH)
        val DUMMY_VERIFIER = ByteArray(VerifierHasher.LENGTH)
    }
}
