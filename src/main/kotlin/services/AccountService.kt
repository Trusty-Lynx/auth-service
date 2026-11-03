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

// Damit wird bei unbekannten E-Mails weitergerechnet, siehe authenticate().
private const val DUMMY_USER_ID = "00000000-0000-0000-0000-000000000000"
private val DUMMY_SALT = ByteArray(VerifierHasher.SALT_LENGTH)
private val DUMMY_VERIFIER = ByteArray(VerifierHasher.LENGTH)

/** Alles rund ums Konto: Prelogin, Registrierung, Login und TOTP einrichten. */
class AccountService(
    private val users: UserRepository,
    private val sessions: SessionService,
    private val keys: KeyServiceClient,
    private val rateLimiter: LoginRateLimiter,
    private val preloginSecret: ByteArray,
    private val clock: Clock,
) {

    /** Salt und KDF-Parameter. Unbekannte E-Mails bekommen ein Fake-Salt, das pro E-Mail immer gleich ist. */
    suspend fun prelogin(email: String): PreloginResponse {
        val user = users.findByEmail(email)
        val fakeSalt = hmacSha256(preloginSecret, email.toByteArray(Charsets.UTF_8)).copyOf(SALT_LOGIN_LENGTH)
        return if (user != null) {
            PreloginResponse(salt = user.saltLogin.toBase64(), kdf = user.kdfLogin)
        } else {
            PreloginResponse(salt = fakeSalt.toBase64(), kdf = KdfParams.DEFAULT)
        }
    }

    /** Legt das Konto mit TOTP, seine Schlüssel beim Key-Service und die erste Session an. */
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
            // Der Code aus der Registrierung zählt als benutzt und taugt nicht mehr für einen Login.
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

    /** Richtet TOTP für ein Konto von vor TOTP ein. Hat das Konto schon TOTP, kommt `409`. */
    suspend fun enableTotp(userId: String, totpSecret: ByteArray, totpCode: String) {
        val totpStep = firstTotpStep(totpSecret, totpCode)
        if (!users.enableTotp(userId, totpSecret, totpStep)) throw AlreadyExists()
    }

    private suspend fun authenticate(email: String, loginKey: ByteArray): User {
        val user = users.findByEmail(email)
        // Bei unbekannter E-Mail mit Dummy-Werten weiterrechnen, damit beide Fälle gleich lange dauern.
        val h = keys.pepperHmac(user?.userId ?: DUMMY_USER_ID, loginKey)
        val verifierMatches = withContext(Dispatchers.Default) {
            VerifierHasher.matches(h, user?.saltSLogin ?: DUMMY_SALT, user?.loginVerifier ?: DUMMY_VERIFIER)
        }
        if (user == null || !verifierMatches) throw Unauthorized()
        return user
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
    private fun firstTotpStep(secret: ByteArray, code: String): Long =
        Totp.matchingStep(secret, code, clock.instant(), lastUsedStep = null)
            ?: throw BadRequest("invalid_totp")

    /** Login-Verifier = Argon2id(h, salt_s_login) mit h = HMAC(pepper_login, loginKey) vom Key-Service. */
    private suspend fun loginVerifier(userId: String, loginKey: ByteArray, saltSLogin: ByteArray): ByteArray {
        val h = keys.pepperHmac(userId, loginKey)
        return withContext(Dispatchers.Default) { VerifierHasher.hash(h, saltSLogin) }
    }
}
