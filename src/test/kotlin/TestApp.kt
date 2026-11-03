package de.trustylynx

import de.trustylynx.models.ErrorResponse
import de.trustylynx.models.KdfParams
import de.trustylynx.models.LoginRequest
import de.trustylynx.models.RegisterRequest
import de.trustylynx.models.RegisterResponse
import de.trustylynx.models.TotpSetupRequest
import de.trustylynx.models.VerifySessionRequest
import de.trustylynx.plugins.AppJson
import de.trustylynx.plugins.CorsConfig
import de.trustylynx.security.LoginRateLimiter
import de.trustylynx.security.Totp
import de.trustylynx.security.toBase64
import de.trustylynx.services.AccountService
import de.trustylynx.services.SessionService
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.time.Instant

const val EMAIL = "alice@example.com"
const val GATEWAY_TOKEN = "test-gateway-token"
val TOKEN_TTL: Duration = Duration.ofHours(12)
val LOGIN_KEY = ByteArray(32) { 2 }
val SALT_LOGIN = ByteArray(16) { 1 }
val TOTP_SECRET = ByteArray(20) { 4 }

fun authTest(cors: CorsConfig = CorsConfig(), block: suspend AuthTestEnv.() -> Unit) = testApplication {
    environment { config = MapApplicationConfig() }
    val env = AuthTestEnv()
    application { configureApp(env.accounts, env.sessions, GATEWAY_TOKEN, cors) { env.healthy } }
    env.client = client
    env.block()
}

class AuthTestEnv {
    val clock = MutableClock(Instant.parse("2026-10-08T10:00:00Z"))
    val sessionRepository = InMemorySessionRepository()
    val users = InMemoryUserRepository(sessionRepository)
    val keys = FakeKeyServiceClient()
    val sessions = SessionService(sessionRepository, clock, TOKEN_TTL)
    val accounts = AccountService(
        users = users,
        sessions = sessions,
        keys = keys,
        rateLimiter = LoginRateLimiter(clock),
        preloginSecret = ByteArray(32) { 9 },
        clock = clock,
    )
    var healthy = true
    lateinit var client: HttpClient

    suspend fun prelogin(email: String?): HttpResponse = client.get("/prelogin") {
        if (email != null) parameter("email", email)
    }

    /** Der aktuelle Code der Authenticator-App zur Test-Uhr. */
    fun totpCode(secret: ByteArray = TOTP_SECRET): String = Totp.code(secret, Totp.step(clock.instant()))

    /** Ein gültig aussehender Code, der gerade nicht passt. */
    fun wrongTotpCode(): String = Totp.code(TOTP_SECRET, Totp.step(clock.instant()) + 5)

    /** Wartet auf den nächsten Code. Jeder Code gilt pro Konto nur einmal, auch der aus der Registrierung. */
    fun nextTotpCode() = clock.advance(Totp.PERIOD)

    suspend fun register(email: String = EMAIL, totp: TotpSetupRequest = totpSetup()): HttpResponse =
        client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(
                AppJson.encodeToString(
                    RegisterRequest(
                        email = email,
                        salt = SALT_LOGIN.toBase64(),
                        kdf = KdfParams.DEFAULT,
                        loginKey = LOGIN_KEY.toBase64(),
                        totp = totp,
                    ),
                ),
            )
        }

    suspend fun registered(email: String = EMAIL): RegisterResponse {
        val response = register(email)
        check(response.status.value == 201) { "registration failed: ${response.status} ${response.bodyAsText()}" }
        return AppJson.decodeFromString(response.bodyAsText())
    }

    /** Ein Konto von vor TOTP: registriert, dann das Secret wieder entfernt. */
    suspend fun legacyAccount(email: String = EMAIL): RegisterResponse {
        val registered = registered(email)
        users.removeTotp(registered.userId)
        return registered
    }

    suspend fun login(
        email: String = EMAIL,
        loginKey: ByteArray = LOGIN_KEY,
        totp: String? = totpCode(),
    ): HttpResponse = client.post("/login") {
        contentType(ContentType.Application.Json)
        setBody(AppJson.encodeToString(LoginRequest(email = email, loginKey = loginKey.toBase64(), totp = totp)))
    }

    suspend fun enableTotp(token: String?, totp: TotpSetupRequest = totpSetup()): HttpResponse =
        client.post("/account/totp") {
            if (token != null) bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(AppJson.encodeToString(totp))
        }

    fun totpSetup(secret: ByteArray = TOTP_SECRET, code: String = totpCode(secret)) =
        TotpSetupRequest(secret = secret.toBase64(), code = code)

    suspend fun logout(token: String?): HttpResponse = client.post("/logout") {
        if (token != null) bearerAuth(token)
    }

    suspend fun verify(token: String, serviceToken: String? = GATEWAY_TOKEN): HttpResponse =
        client.post("/internal/sessions/verify") {
            contentType(ContentType.Application.Json)
            if (serviceToken != null) header(SERVICE_TOKEN_HEADER, serviceToken)
            setBody(AppJson.encodeToString(VerifySessionRequest(token = token)))
        }
}

suspend fun HttpResponse.error(): ErrorResponse = AppJson.decodeFromString(bodyAsText())
