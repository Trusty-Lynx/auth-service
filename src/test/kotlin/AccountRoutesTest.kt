package de.trustylynx

import de.trustylynx.api.LoginDeviceDto
import de.trustylynx.api.LoginResponse
import de.trustylynx.api.PreloginResponse
import de.trustylynx.api.RegisterResponse
import de.trustylynx.api.VerifySessionResponse
import de.trustylynx.common.AppJson
import de.trustylynx.common.DEVICE_ID_HEADER
import de.trustylynx.common.ErrorResponse
import de.trustylynx.common.decodeBase64OrNull
import de.trustylynx.common.toBase64
import de.trustylynx.domain.DeviceStatus
import de.trustylynx.domain.KdfParams
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountRoutesTest {

    @Test
    fun `T1 valid registration returns 201 and a token that passes verify`() = authTest {
        val response = register()

        assertEquals(HttpStatusCode.Created, response.status)
        val body = AppJson.decodeFromString<RegisterResponse>(response.bodyAsText())
        val verified = verify(body.token)
        assertEquals(HttpStatusCode.OK, verified.status)
        assertEquals(body.userId, AppJson.decodeFromString<VerifySessionResponse>(verified.bodyAsText()).userId)
        assertEquals(listOf(body.userId), keys.createdUserKeys)
    }

    @Test
    fun `T3 key-service failure leaves no user`() = authTest {
        keys.failCreateUserKeys = true

        val response = register()

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(users.users.isEmpty())
        assertTrue(sessionRepository.sessions.isEmpty())
    }

    @Test
    fun `T4 duplicate email returns 409`() = authTest {
        assertEquals(HttpStatusCode.Created, register(email = EMAIL).status)

        val response = register(email = " Alice@Example.com ")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ErrorResponse("already_exists"), response.error())
    }

    @Test
    fun `invalid email returns 400`() = authTest {
        val response = register(email = "not-an-email")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorResponse("bad_request", "invalid email"), response.error())
    }

    @Test
    fun `malformed json returns 400`() = authTest {
        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            header(DEVICE_ID_HEADER, DEVICE_A)
            setBody("{\"email\":")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_request", response.error().error)
    }

    @Test
    fun `T5 prelogin returns the real salt for a known email`() = authTest {
        registered()

        val response = prelogin(EMAIL)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = AppJson.decodeFromString<PreloginResponse>(response.bodyAsText())
        assertEquals(SALT_LOGIN.toBase64(), body.salt)
        assertEquals(KdfParams.DEFAULT, body.kdf)
    }

    @Test
    fun `T6 prelogin returns the same fake salt twice for an unknown email`() = authTest {
        val first = prelogin("nobody@example.com")
        val second = prelogin("nobody@example.com")

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.OK, second.status)
        val firstBody = AppJson.decodeFromString<PreloginResponse>(first.bodyAsText())
        assertEquals(firstBody, AppJson.decodeFromString<PreloginResponse>(second.bodyAsText()))
        assertEquals(16, firstBody.salt.decodeBase64OrNull()?.size)
        assertEquals(KdfParams.DEFAULT, firstBody.kdf)
    }

    @Test
    fun `prelogin without or with invalid email returns 400`() = authTest {
        assertEquals(HttpStatusCode.BadRequest, prelogin(null).status)
        assertEquals(HttpStatusCode.BadRequest, prelogin("not-an-email").status)
    }

    @Test
    fun `T7 login with correct data returns 200 with token, userId and deviceStatus`() = authTest {
        val registered = registered()

        val response = login()

        assertEquals(HttpStatusCode.OK, response.status)
        val body = AppJson.decodeFromString<LoginResponse>(response.bodyAsText())
        assertEquals(registered.userId, body.userId)
        assertEquals(DeviceStatus.TRUSTED, body.deviceStatus)
        assertEquals(HttpStatusCode.OK, verify(body.token).status)
    }

    @Test
    fun `T8 wrong loginKey and unknown email return the same 401`() = authTest {
        registered()

        val wrongKey = login(loginKey = ByteArray(32) { 3 })
        val unknownEmail = login(email = "nobody@example.com")

        for (response in listOf(wrongKey, unknownEmail)) {
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("""{"error":"unauthorized"}""", response.bodyAsText())
        }
    }

    @Test
    fun `T9 sixth attempt within 60 s returns 429 even with correct data`() = authTest {
        registered()
        repeat(5) {
            assertEquals(HttpStatusCode.Unauthorized, login(loginKey = ByteArray(32) { 3 }).status)
        }

        val blocked = login()

        assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
        assertEquals("60", blocked.headers[HttpHeaders.RetryAfter])

        clock.advance(Duration.ofSeconds(61))
        assertEquals(HttpStatusCode.OK, login().status)
    }

    @Test
    fun `unknown emails count against the rate limit too`() = authTest {
        repeat(5) { login(email = "nobody@example.com") }

        assertEquals(HttpStatusCode.TooManyRequests, login(email = "nobody@example.com").status)
    }

    @Test
    fun `T10 second login on the same device invalidates the old token`() = authTest {
        registered()
        val first = AppJson.decodeFromString<LoginResponse>(login().bodyAsText())

        val second = AppJson.decodeFromString<LoginResponse>(login().bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, verify(first.token).status)
        assertEquals(HttpStatusCode.OK, verify(second.token).status)
    }

    @Test
    fun `login with an invalid public key for a new device returns 400`() = authTest {
        registered()

        val response = login(deviceId = DEVICE_B, device = LoginDeviceDto(name = "Linux", publicKey = SALT_LOGIN.toBase64()))

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `T11 logout returns 204, then verify and a second logout return 401`() = authTest {
        val token = registered().token

        assertEquals(HttpStatusCode.NoContent, logout(token).status)

        assertEquals(HttpStatusCode.Unauthorized, verify(token).status)
        assertEquals(HttpStatusCode.Unauthorized, logout(token).status)
    }

    @Test
    fun `logout keeps the sessions of other devices`() = authTest {
        val tokenA = registered().token
        val tokenB = AppJson.decodeFromString<LoginResponse>(login(deviceId = DEVICE_B).bodyAsText()).token

        assertEquals(HttpStatusCode.NoContent, logout(tokenA).status)

        assertEquals(HttpStatusCode.OK, verify(tokenB, deviceId = DEVICE_B).status)
    }

    @Test
    fun `logout without token or from another device returns 401`() = authTest {
        val token = registered().token

        assertEquals(HttpStatusCode.Unauthorized, logout(null).status)
        assertEquals(HttpStatusCode.Unauthorized, logout(token, deviceId = DEVICE_B).status)
        assertEquals(HttpStatusCode.OK, verify(token).status)
    }
}
