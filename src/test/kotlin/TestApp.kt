package de.trustylynx

import de.trustylynx.api.LoginRequest
import de.trustylynx.api.RegisterRequest
import de.trustylynx.api.RegisterResponse
import de.trustylynx.api.VerifySessionRequest
import de.trustylynx.common.AppJson
import de.trustylynx.common.CorsConfig
import de.trustylynx.common.ErrorResponse
import de.trustylynx.common.SERVICE_TOKEN_HEADER
import de.trustylynx.common.toBase64
import de.trustylynx.domain.AccountService
import de.trustylynx.domain.KdfParams
import de.trustylynx.domain.SessionService
import de.trustylynx.security.LoginRateLimiter
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

fun authTest(cors: CorsConfig = CorsConfig(), block: suspend AuthTestEnv.() -> Unit) = testApplication {
    environment { config = MapApplicationConfig() }
    val env = AuthTestEnv()
    application { configureApp(env.accounts, env.sessions, GATEWAY_TOKEN, cors) { env.healthy } }
    env.client = client
    env.block()
}

class AuthTestEnv {
    val clock = MutableClock(Instant.parse("2026-10-08T10:00:00Z"))
    val users = InMemoryUserRepository()
    val sessionRepository = InMemorySessionRepository()
    val keys = FakeKeyServiceClient()
    val sessions = SessionService(sessionRepository, clock, TOKEN_TTL)
    val accounts = AccountService(
        users = users,
        sessions = sessions,
        keys = keys,
        transactions = InMemoryTransactions(users, sessionRepository),
        rateLimiter = LoginRateLimiter(clock),
        preloginSecret = ByteArray(32) { 9 },
    )
    var healthy = true
    lateinit var client: HttpClient

    suspend fun prelogin(email: String?): HttpResponse = client.get("/prelogin") {
        if (email != null) parameter("email", email)
    }

    suspend fun register(email: String = EMAIL): HttpResponse =
        client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(
                AppJson.encodeToString(
                    RegisterRequest(
                        email = email,
                        salt = SALT_LOGIN.toBase64(),
                        kdf = KdfParams.DEFAULT,
                        loginKey = LOGIN_KEY.toBase64(),
                    ),
                ),
            )
        }

    suspend fun registered(email: String = EMAIL): RegisterResponse {
        val response = register(email)
        check(response.status.value == 201) { "registration failed: ${response.status} ${response.bodyAsText()}" }
        return AppJson.decodeFromString(response.bodyAsText())
    }

    suspend fun login(email: String = EMAIL, loginKey: ByteArray = LOGIN_KEY): HttpResponse = client.post("/login") {
        contentType(ContentType.Application.Json)
        setBody(AppJson.encodeToString(LoginRequest(email = email, loginKey = loginKey.toBase64())))
    }

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
