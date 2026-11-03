package de.trustylynx

import de.trustylynx.models.ErrorResponse
import de.trustylynx.models.KdfParams
import de.trustylynx.models.LoginResponse
import de.trustylynx.models.PreloginResponse
import de.trustylynx.models.RegisterResponse
import de.trustylynx.models.VerifySessionResponse
import de.trustylynx.plugins.AppJson
import de.trustylynx.security.Totp
import de.trustylynx.security.decodeBase64OrNull
import de.trustylynx.security.toBase64
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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
        assertContentEquals(TOTP_SECRET, users.users.getValue(body.userId).totpSecret)
    }

    @Test
    fun `T2 registration with a wrong totp code returns 400 invalid_totp and stores nothing`() = authTest {
        val response = register(totp = totpSetup(code = wrongTotpCode()))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorResponse("bad_request", "invalid_totp"), response.error())
        assertTrue(users.users.isEmpty())
        assertTrue(sessionRepository.sessions.isEmpty())
        assertTrue(keys.createdUserKeys.isEmpty())
    }

    @Test
    fun `registration accepts the code of the previous and the next time step`() = authTest {
        val step = Totp.step(clock.instant())

        assertEquals(HttpStatusCode.Created, register("a@example.com", totpSetup(code = Totp.code(TOTP_SECRET, step - 1))).status)
        assertEquals(HttpStatusCode.Created, register("b@example.com", totpSetup(code = Totp.code(TOTP_SECRET, step + 1))).status)
        assertEquals(HttpStatusCode.BadRequest, register("c@example.com", totpSetup(code = Totp.code(TOTP_SECRET, step + 2))).status)
    }

    @Test
    fun `registration without totp returns 400`() = authTest {
        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"email":"$EMAIL","salt":"${SALT_LOGIN.toBase64()}","kdf":${AppJson.encodeToString(KdfParams.DEFAULT)},""" +
                    """"loginKey":"${LOGIN_KEY.toBase64()}"}""",
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorResponse("bad_request", "malformed request body"), response.error())
    }

    @Test
    fun `registration with a malformed totp secret or code returns 400`() = authTest {
        val shortSecret = register(totp = totpSetup().copy(secret = ByteArray(16).toBase64()))
        val fiveDigits = register(totp = totpSetup(code = "12345"))
        val withSpace = register(totp = totpSetup(code = "123 456"))

        assertEquals(ErrorResponse("bad_request", "invalid base64 length, expected 20 bytes"), shortSecret.error())
        for (response in listOf(fiveDigits, withSpace)) {
            assertEquals(ErrorResponse("bad_request", "totp code must be 6 digits"), response.error())
        }
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
    fun `T7 login with correct data returns 200 with token and userId`() = authTest {
        val registered = registered()
        nextTotpCode()

        val response = login()

        assertEquals(HttpStatusCode.OK, response.status)
        val body = AppJson.decodeFromString<LoginResponse>(response.bodyAsText())
        assertEquals(registered.userId, body.userId)
        assertTrue(body.totpEnabled)
        assertEquals(HttpStatusCode.OK, verify(body.token).status)
    }

    @Test
    fun `T8 wrong loginKey, wrong or missing totp and unknown email return the same 401`() = authTest {
        registered()
        nextTotpCode()

        val wrongKey = login(loginKey = ByteArray(32) { 3 })
        val wrongTotp = login(totp = wrongTotpCode())
        val missingTotp = login(totp = null)
        val unknownEmail = login(email = "nobody@example.com")

        for (response in listOf(wrongKey, wrongTotp, missingTotp, unknownEmail)) {
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("""{"error":"unauthorized"}""", response.bodyAsText())
        }
        assertEquals(1, sessionRepository.sessions.size, "only the session from the registration")
    }

    @Test
    fun `an explicit null totp counts as no code`() = authTest {
        legacyAccount()
        val body = """{"email":"$EMAIL","loginKey":"${LOGIN_KEY.toBase64()}","totp":null}"""

        val response = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `login with a malformed totp code returns 400`() = authTest {
        registered()

        val response = login(totp = "12a456")

        assertEquals(ErrorResponse("bad_request", "totp code must be 6 digits"), response.error())
    }

    @Test
    fun `LOG-9 every totp code works only once`() = authTest {
        registered()

        assertEquals(HttpStatusCode.Unauthorized, login().status, "code from the registration")

        nextTotpCode()
        assertEquals(HttpStatusCode.OK, login().status)
        assertEquals(HttpStatusCode.Unauthorized, login().status, "same code again")

        nextTotpCode()
        assertEquals(HttpStatusCode.OK, login().status)
    }

    @Test
    fun `an older code is rejected once a newer one was used`() = authTest {
        registered()
        nextTotpCode()
        nextTotpCode()
        val step = Totp.step(clock.instant())

        assertEquals(HttpStatusCode.OK, login(totp = Totp.code(TOTP_SECRET, step + 1)).status)

        assertEquals(HttpStatusCode.Unauthorized, login(totp = Totp.code(TOTP_SECRET, step)).status)
    }

    @Test
    fun `wrong totp codes count against the rate limit`() = authTest {
        registered()
        nextTotpCode()
        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, login(totp = wrongTotpCode()).status) }

        assertEquals(HttpStatusCode.TooManyRequests, login().status)
    }

    @Test
    fun `an account from before totp logs in without a code and with totpEnabled false`() = authTest {
        legacyAccount()

        for (totp in listOf(null, "123456")) {
            val response = login(totp = totp)

            assertEquals(HttpStatusCode.OK, response.status)
            assertFalse(AppJson.decodeFromString<LoginResponse>(response.bodyAsText()).totpEnabled)
        }
    }

    @Test
    fun `enabling totp returns 204, after that the login needs a code`() = authTest {
        val registered = legacyAccount()

        assertEquals(HttpStatusCode.NoContent, enableTotp(registered.token).status)

        val user = users.users.getValue(registered.userId)
        assertContentEquals(TOTP_SECRET, user.totpSecret)
        assertEquals(Totp.step(clock.instant()), user.totpLastStep)
        assertEquals(HttpStatusCode.Unauthorized, login(totp = null).status)
        assertEquals(HttpStatusCode.Unauthorized, login().status, "code from the setup")
        nextTotpCode()
        val response = login()
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(AppJson.decodeFromString<LoginResponse>(response.bodyAsText()).totpEnabled)
    }

    @Test
    fun `enabling totp twice returns 409 and keeps the first secret`() = authTest {
        val token = legacyAccount().token
        assertEquals(HttpStatusCode.NoContent, enableTotp(token).status)

        val otherSecret = ByteArray(20) { 5 }
        val response = enableTotp(token, totpSetup(secret = otherSecret))

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ErrorResponse("already_exists"), response.error())
        assertContentEquals(TOTP_SECRET, users.users.values.single().totpSecret)
    }

    @Test
    fun `an account registered with totp cannot enable it again`() = authTest {
        val token = registered().token

        assertEquals(HttpStatusCode.Conflict, enableTotp(token, totpSetup(secret = ByteArray(20) { 5 })).status)
    }

    @Test
    fun `enabling totp with a wrong code returns 400 invalid_totp`() = authTest {
        val registered = legacyAccount()

        val response = enableTotp(registered.token, totpSetup(code = wrongTotpCode()))

        assertEquals(ErrorResponse("bad_request", "invalid_totp"), response.error())
        assertNull(users.users.getValue(registered.userId).totpSecret)
    }

    @Test
    fun `enabling totp without a valid token returns 401 even with a broken body`() = authTest {
        legacyAccount()

        assertEquals(HttpStatusCode.Unauthorized, enableTotp(null).status)
        assertEquals(HttpStatusCode.Unauthorized, enableTotp("not-a-real-token").status)
        val brokenBody = client.post("/account/totp") {
            bearerAuth("not-a-real-token")
            contentType(ContentType.Application.Json)
            setBody("{\"secret\":")
        }
        assertEquals(HttpStatusCode.Unauthorized, brokenBody.status)
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
    fun `T10 two logins return two different tokens that both pass verify`() = authTest {
        registered()
        nextTotpCode()
        val first = AppJson.decodeFromString<LoginResponse>(login().bodyAsText())

        nextTotpCode()
        val second = AppJson.decodeFromString<LoginResponse>(login().bodyAsText())

        assertNotEquals(first.token, second.token)
        assertEquals(HttpStatusCode.OK, verify(first.token).status)
        assertEquals(HttpStatusCode.OK, verify(second.token).status)
    }

    @Test
    fun `T11 logout returns 204, then verify and a second logout return 401`() = authTest {
        val token = registered().token

        assertEquals(HttpStatusCode.NoContent, logout(token).status)

        assertEquals(HttpStatusCode.Unauthorized, verify(token).status)
        assertEquals(HttpStatusCode.Unauthorized, logout(token).status)
    }

    @Test
    fun `logout keeps the other sessions of the user`() = authTest {
        registered()
        nextTotpCode()
        val first = AppJson.decodeFromString<LoginResponse>(login().bodyAsText()).token
        nextTotpCode()
        val second = AppJson.decodeFromString<LoginResponse>(login().bodyAsText()).token

        assertEquals(HttpStatusCode.NoContent, logout(first).status)

        assertEquals(HttpStatusCode.Unauthorized, verify(first).status)
        assertEquals(HttpStatusCode.OK, verify(second).status)
    }

    @Test
    fun `logout without token returns 401`() = authTest {
        val token = registered().token

        assertEquals(HttpStatusCode.Unauthorized, logout(null).status)
        assertEquals(HttpStatusCode.OK, verify(token).status)
    }
}
