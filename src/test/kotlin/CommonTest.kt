package de.trustylynx

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class CommonTest {

    @Test
    fun `health returns ok while the database is reachable`() = authTest {
        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test
    fun `health returns 503 when the database is not reachable`() = authTest {
        healthy = false

        val response = client.get("/health")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("""{"status":"unavailable"}""", response.bodyAsText())
    }

    @Test
    fun `every response carries a request id`() = authTest {
        val response = prelogin(EMAIL)

        assertNotNull(response.headers[HttpHeaders.XRequestId])
    }

    @Test
    fun `an incoming request id is passed through`() = authTest {
        val response = client.get("/health") { header(HttpHeaders.XRequestId, "req-123") }

        assertEquals("req-123", response.headers[HttpHeaders.XRequestId])
    }

    @Test
    fun `an invalid request id is replaced`() = authTest {
        val response = client.get("/health") { header(HttpHeaders.XRequestId, "bad id!") }

        val requestId = assertNotNull(response.headers[HttpHeaders.XRequestId])
        assertNotEquals("bad id!", requestId)
    }
}
