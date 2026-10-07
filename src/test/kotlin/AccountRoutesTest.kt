package de.trustylynx

import de.trustylynx.api.DeviceDto
import de.trustylynx.api.RegisterRequest
import de.trustylynx.api.RegisterResponse
import de.trustylynx.clients.KeyServiceClient
import de.trustylynx.common.AlreadyExists
import de.trustylynx.common.AppJson
import de.trustylynx.common.DEVICE_ID_HEADER
import de.trustylynx.common.ErrorResponse
import de.trustylynx.common.toBase64
import de.trustylynx.domain.AccountService
import de.trustylynx.domain.KdfParams
import de.trustylynx.domain.User
import de.trustylynx.persistence.UserRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import org.bouncycastle.asn1.x9.ECNamedCurveTable
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountRoutesTest {

    @Test
    fun `valid registration returns 201`() = withApp { client ->
        val response = client.register(validRequest())

        assertEquals(HttpStatusCode.Created, response.status)
        val body = AppJson.decodeFromString<RegisterResponse>(response.bodyAsText())
        assertTrue(body.userId.isNotBlank())
    }

    @Test
    fun `invalid email returns 400`() = withApp { client ->
        val response = client.register(validRequest(email = "not-an-email"))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorResponse("bad_request", "invalid email"), response.error())
    }

    @Test
    fun `duplicate email returns 409`() = withApp { client ->
        assertEquals(HttpStatusCode.Created, client.register(validRequest(email = "alice@example.com")).status)

        val response = client.register(validRequest(email = " Alice@Example.com "))

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ErrorResponse("already_exists"), response.error())
    }

    @Test
    fun `malformed json returns 400`() = withApp { client ->
        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            header(DEVICE_ID_HEADER, DEVICE_ID)
            setBody("{\"email\":")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.error().error)
    }

    private fun withApp(block: suspend (HttpClient) -> Unit) = testApplication {
        // Leere Config, damit application.yaml nicht module() mit echter DB lädt.
        environment { config = MapApplicationConfig() }
        application { configureApp(AccountService(InMemoryUserRepository(), FakeKeyServiceClient())) }
        block(client)
    }

    private suspend fun HttpClient.register(request: RegisterRequest): HttpResponse = post("/register") {
        contentType(ContentType.Application.Json)
        header(DEVICE_ID_HEADER, DEVICE_ID)
        setBody(AppJson.encodeToString(request))
    }

    private suspend fun HttpResponse.error(): ErrorResponse = AppJson.decodeFromString(bodyAsText())

    private fun validRequest(email: String = "alice@example.com") = RegisterRequest(
        email = email,
        salt = ByteArray(16) { 1 }.toBase64(),
        kdf = KdfParams.DEFAULT,
        loginKey = ByteArray(32) { 2 }.toBase64(),
        device = DeviceDto(id = DEVICE_ID, name = "Test Device", publicKey = P256_PUBLIC_KEY),
    )

    private companion object {
        const val DEVICE_ID = "device-1"

        /** Generatorpunkt von P-256: ein gültiger, unkomprimierter Public Key. */
        val P256_PUBLIC_KEY: String = ECNamedCurveTable.getByName("secp256r1").g.getEncoded(false).toBase64()
    }
}

private class InMemoryUserRepository : UserRepository {
    private val users = ConcurrentHashMap<String, User>()

    override suspend fun create(user: User) {
        if (users.values.any { it.email == user.email }) throw AlreadyExists()
        if (users.putIfAbsent(user.userId, user) != null) throw AlreadyExists()
    }

    override suspend fun findByEmail(email: String): User? = users.values.firstOrNull { it.email == email }

    override suspend fun findById(userId: String): User? = users[userId]

    override suspend fun updateLoginCredentials(
        userId: String,
        saltLogin: ByteArray,
        kdfLogin: KdfParams,
        saltSLogin: ByteArray,
        loginVerifier: ByteArray,
    ) = error("not used in tests")

    override suspend fun delete(userId: String) {
        users.remove(userId)
    }
}

private class FakeKeyServiceClient : KeyServiceClient {
    override suspend fun pepperHmac(userId: String, loginKey: ByteArray): ByteArray = ByteArray(32) { 7 }

    override suspend fun createUserKeys(userId: String) = Unit

    override suspend fun deleteUserKeys(userId: String) = Unit
}
