package de.trustylynx.persistence

import de.trustylynx.common.AlreadyExists
import de.trustylynx.common.DbConfig
import de.trustylynx.common.createDataSource
import de.trustylynx.common.migrateDatabase
import de.trustylynx.domain.KdfParams
import de.trustylynx.domain.Session
import de.trustylynx.domain.User
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.SQLException
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresRepositoryTest {

    private val users by lazy { PostgresUserRepository(database) }
    private val sessions by lazy { PostgresSessionRepository(database) }

    @BeforeTest
    fun cleanDatabase() {
        assumeTrue("Docker is not available", DockerClientFactory.instance().isDockerAvailable)
        database.connection.use { it.createStatement().execute("TRUNCATE users, sessions") }
    }

    @Test
    fun `a created user is found by email with all fields`() = runBlocking {
        users.create(user(), session("session-1", hash = 1))

        val found = assertNotNull(users.findByEmail("alice@example.com"))
        assertEquals("user-1", found.userId)
        assertEquals(KdfParams.DEFAULT, found.kdfLogin)
        assertContentEquals(ByteArray(16) { 1 }, found.saltLogin)
        assertContentEquals(ByteArray(16) { 2 }, found.saltSLogin)
        assertContentEquals(ByteArray(32) { 3 }, found.loginVerifier)
        assertContentEquals(ByteArray(20) { 4 }, found.totpSecret)
        assertEquals(100L, found.totpLastStep)
    }

    @Test
    fun `a user without totp is stored with null`() = runBlocking {
        users.create(user(totp = false), session("session-1", hash = 1))

        val found = assertNotNull(users.findByEmail("alice@example.com"))
        assertNull(found.totpSecret)
        assertNull(found.totpLastStep)
    }

    @Test
    fun `a totp step is only marked as used when it is newer`() = runBlocking {
        users.create(user(), session("session-1", hash = 1))

        assertFalse(users.markTotpStepUsed("user-1", 100))
        assertFalse(users.markTotpStepUsed("user-1", 99))
        assertTrue(users.markTotpStepUsed("user-1", 101))
        assertFalse(users.markTotpStepUsed("user-1", 101))

        assertEquals(101L, users.findByEmail("alice@example.com")?.totpLastStep)
    }

    @Test
    fun `no totp step is marked for a user without totp`() = runBlocking {
        users.create(user(totp = false), session("session-1", hash = 1))

        assertFalse(users.markTotpStepUsed("user-1", 101))
    }

    @Test
    fun `totp is only enabled for a user without totp`() = runBlocking {
        users.create(user(totp = false), session("session-1", hash = 1))

        assertTrue(users.enableTotp("user-1", ByteArray(20) { 5 }, 200))
        assertFalse(users.enableTotp("user-1", ByteArray(20) { 6 }, 201))

        val found = assertNotNull(users.findByEmail("alice@example.com"))
        assertContentEquals(ByteArray(20) { 5 }, found.totpSecret)
        assertEquals(200L, found.totpLastStep)
    }

    @Test
    fun `a totp secret with the wrong length is rejected by the database`() = runBlocking {
        users.create(user(totp = false), session("session-1", hash = 1))

        assertFailsWith<SQLException> { users.enableTotp("user-1", ByteArray(16), 200) }
        Unit
    }

    @Test
    fun `an unknown email returns null`() = runBlocking {
        assertNull(users.findByEmail("nobody@example.com"))
    }

    @Test
    fun `a second user with the same email is rejected`() = runBlocking {
        users.create(user(userId = "user-1"), session("session-1", hash = 1))

        assertFailsWith<AlreadyExists> {
            users.create(user(userId = "user-2"), session("session-2", hash = 2, userId = "user-2"))
        }
        assertNull(sessions.findByTokenHash(tokenHash(2)))
    }

    @Test
    fun `a user and a later session are both stored`() = runBlocking {
        users.create(user(), session("session-1", hash = 1))

        sessions.create(session("session-2", hash = 2))

        val first = assertNotNull(sessions.findByTokenHash(tokenHash(1)))
        assertEquals("session-1", first.sessionId)
        assertEquals("user-1", first.userId)
        assertEquals(EXPIRES_AT, first.expiresAt)
        val second = assertNotNull(sessions.findByTokenHash(tokenHash(2)))
        assertEquals("session-2", second.sessionId)
        assertEquals("user-1", second.userId)
    }

    @Test
    fun `delete reports whether the session existed`() = runBlocking {
        users.create(user(), session("session-1", hash = 1))

        assertTrue(sessions.delete("session-1"))
        assertFalse(sessions.delete("session-1"))
    }

    @Test
    fun `a user is not stored when its first session fails`() = runBlocking {
        // Ein Token-Hash mit 31 statt 32 Bytes verletzt den CHECK in der Tabelle sessions.
        val brokenSession = Session("session-1", "user-1", ByteArray(31), CREATED_AT, EXPIRES_AT)

        assertFailsWith<SQLException> { users.create(user(), brokenSession) }

        assertNull(users.findByEmail("alice@example.com"))
    }

    private fun user(userId: String = "user-1", totp: Boolean = true) = User(
        userId = userId,
        email = "alice@example.com",
        saltLogin = ByteArray(16) { 1 },
        kdfLogin = KdfParams.DEFAULT,
        saltSLogin = ByteArray(16) { 2 },
        loginVerifier = ByteArray(32) { 3 },
        totpSecret = if (totp) ByteArray(20) { 4 } else null,
        totpLastStep = if (totp) 100L else null,
    )

    private fun session(sessionId: String, hash: Int, userId: String = "user-1") = Session(
        sessionId = sessionId,
        userId = userId,
        tokenHash = tokenHash(hash),
        createdAt = CREATED_AT,
        expiresAt = EXPIRES_AT,
    )

    private fun tokenHash(seed: Int) = ByteArray(32) { seed.toByte() }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-10-08T10:00:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-10-08T22:00:00Z")

        val database: DataSource by lazy {
            val container = PostgreSQLContainer("postgres:17").apply { start() }
            createDataSource(DbConfig(container.jdbcUrl, container.username, container.password))
                .also(::migrateDatabase)
        }
    }
}
