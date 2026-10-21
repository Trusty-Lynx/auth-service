package de.trustylynx

import de.trustylynx.common.CorsConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LIVE_SERVER = "http://127.0.0.1:5500"
private const val APP_ORIGIN = "https://app.nexvent.de"

class CorsTest {

    @Test
    fun `a local origin may call the api`() = authTest {
        val response = preloginFrom(LIVE_SERVER)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(LIVE_SERVER, response.allowOrigin())
        val exposed = assertNotNull(response.headers[HttpHeaders.AccessControlExposeHeaders])
        assertTrue(HttpHeaders.XRequestId in exposed)
        assertTrue(HttpHeaders.RetryAfter in exposed)
    }

    @Test
    fun `a preflight from a local origin allows json and the bearer token`() = authTest {
        val response = preflight("http://localhost:3000", "content-type,authorization")

        assertTrue(response.status.value in 200..299, "status ${response.status}")
        assertEquals("http://localhost:3000", response.allowOrigin())
        val allowed = assertNotNull(response.headers[HttpHeaders.AccessControlAllowHeaders]).lowercase()
        assertTrue("content-type" in allowed)
        assertTrue("authorization" in allowed)
    }

    @Test
    fun `a preflight with an unknown header is rejected`() = authTest {
        val response = preflight("http://localhost:3000", "content-type,authorization,x-evil")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(response.allowOrigin())
    }

    @Test
    fun `a preflight with x-device-id is rejected`() = authTest {
        val response = preflight("http://localhost:3000", "content-type,x-device-id")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(response.allowOrigin())
    }

    @Test
    fun `error responses carry the cors header`() = authTest {
        val response = client.post("/register") {
            header(HttpHeaders.Origin, LIVE_SERVER)
            contentType(ContentType.Application.Json)
            setBody("{\"email\":")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(LIVE_SERVER, response.allowOrigin())
    }

    @Test
    fun `a configured origin may call the api`() = authTest(CorsConfig(allowedOrigins = setOf(APP_ORIGIN))) {
        val response = preloginFrom(APP_ORIGIN)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(APP_ORIGIN, response.allowOrigin())
    }

    @Test
    fun `the null origin is allowed when enabled`() = authTest(CorsConfig(allowNullOrigin = true)) {
        assertEquals("null", preloginFrom("null").allowOrigin())

        val preflight = preflight("null", "content-type,authorization")
        assertTrue(preflight.status.value in 200..299, "status ${preflight.status}")
        assertEquals("null", preflight.allowOrigin())
    }

    @Test
    fun `the null origin is rejected by default`() = authTest {
        val response = preloginFrom("null")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(response.allowOrigin())
    }

    @Test
    fun `a foreign origin is rejected`() = authTest {
        val response = preloginFrom("https://evil.example")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(response.allowOrigin())
    }

    @Test
    fun `a host that only starts with localhost is rejected`() = authTest {
        val response = preloginFrom("http://localhost.evil.example:5500")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(response.allowOrigin())
    }

    @Test
    fun `requests without origin are not affected`() = authTest {
        val response = prelogin(EMAIL)

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers.names().none { it.startsWith("Access-Control-", ignoreCase = true) })
    }

    @Test
    fun `allowed origins are trimmed and lowercased`() {
        assertEquals(
            setOf(APP_ORIGIN, "http://localhost:8080"),
            CorsConfig.parseAllowedOrigins(" https://App.nexvent.de , ,http://localhost:8080,"),
        )
        assertEquals(emptySet(), CorsConfig.parseAllowedOrigins(""))
    }

    @Test
    fun `invalid allowed origins are rejected`() {
        for (origin in listOf("*", "app.nexvent.de", "https://app.nexvent.de/", "https://app.nexvent.de/api", "null")) {
            assertFailsWith<IllegalArgumentException>(origin) { CorsConfig.parseAllowedOrigins(origin) }
        }
    }

    @Test
    fun `the null origin flag only accepts true or false`() {
        assertTrue(CorsConfig.parseFlag("CORS_ALLOW_NULL_ORIGIN", "true"))
        assertFalse(CorsConfig.parseFlag("CORS_ALLOW_NULL_ORIGIN", "false"))
        assertFailsWith<IllegalStateException> { CorsConfig.parseFlag("CORS_ALLOW_NULL_ORIGIN", "yes") }
    }
}

private suspend fun AuthTestEnv.preloginFrom(origin: String): HttpResponse = client.get("/prelogin") {
    parameter("email", EMAIL)
    header(HttpHeaders.Origin, origin)
}

private suspend fun AuthTestEnv.preflight(origin: String, requestHeaders: String): HttpResponse =
    client.options("/register") {
        header(HttpHeaders.Origin, origin)
        header(HttpHeaders.AccessControlRequestMethod, "POST")
        header(HttpHeaders.AccessControlRequestHeaders, requestHeaders)
    }

private fun HttpResponse.allowOrigin(): String? = headers[HttpHeaders.AccessControlAllowOrigin]
