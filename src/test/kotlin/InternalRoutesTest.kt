package de.trustylynx

import de.trustylynx.models.ErrorResponse
import de.trustylynx.models.VerifySessionResponse
import de.trustylynx.plugins.AppJson
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class InternalRoutesTest {

    @Test
    fun `verify returns the owner of a valid token`() = authTest {
        val registered = registered()

        val response = verify(registered.token)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = AppJson.decodeFromString<VerifySessionResponse>(response.bodyAsText())
        val session = sessionRepository.sessions.values.single()
        assertEquals(registered.userId, body.userId)
        assertEquals(session.sessionId, body.sessionId)
        assertEquals(clock.instant().plus(TOKEN_TTL).epochSecond, body.expiresAt)
    }

    @Test
    fun `T12 unknown token returns 401`() = authTest {
        registered()

        val response = verify("not-a-real-token")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ErrorResponse("unauthorized"), response.error())
    }

    @Test
    fun `T13 verify without or with a wrong service token returns 403`() = authTest {
        val token = registered().token

        assertEquals(HttpStatusCode.Forbidden, verify(token, serviceToken = null).status)
        assertEquals(HttpStatusCode.Forbidden, verify(token, serviceToken = "wrong-token").status)
    }

    @Test
    fun `T14 expired token returns 401`() = authTest {
        val token = registered().token

        clock.advance(TOKEN_TTL)

        assertEquals(HttpStatusCode.Unauthorized, verify(token).status)
    }
}
