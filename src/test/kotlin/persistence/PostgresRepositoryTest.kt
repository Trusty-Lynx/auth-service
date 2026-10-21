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
    private val transactions by lazy { PostgresTransactions(database) }

    @BeforeTest
    fun cleanDatabase() {
        assumeTrue("Docker is not available", DockerClientFactory.instance().isDockerAvailable)
        database.connection.use { it.createStatement().execute("TRUNCATE users, sessions") }
    }

    @Test
    fun `a created user is found by email with all fields`() = runBlocking {
        users.create(user())

        val found = assertNotNull(users.findByEmail("alice@example.com"))
        assertEquals("user-1", found.userId)
        assertEquals(KdfParams.DEFAULT, found.kdfLogin)
        assertContentEquals(ByteArray(16) { 1 }, found.saltLogin)
        assertContentEquals(ByteArray(16) { 2 }, found.saltSLogin)
        assertContentEquals(ByteArray(32) { 3 }, found.loginVerifier)
    }

    @Test
    fun `an unknown email returns null`() = runBlocking {
        assertNull(users.findByEmail("nobody@example.com"))
    }

    @Test
    fun `a second user with the same email is rejected`() = runBlocking {
        users.create(user(userId = "user-1"))

        assertFailsWith<AlreadyExists> { users.create(user(userId = "user-2")) }
        Unit
    }

    @Test
    fun `create stores two sessions for the same user`() = runBlocking {
        users.create(user())

        sessions.create(session("session-1", hash = 1))
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
        users.create(user())
        sessions.create(session("session-1", hash = 1))

        assertTrue(sessions.delete("session-1"))
        assertFalse(sessions.delete("session-1"))
    }

    @Test
    fun `a failed transaction leaves nothing behind`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            transactions.inTransaction {
                users.create(user())
                sessions.create(session("session-1", hash = 1))
                error("later step failed")
            }
        }

        assertNull(users.findByEmail("alice@example.com"))
        assertNull(sessions.findByTokenHash(tokenHash(1)))
    }

    private fun user(userId: String = "user-1") = User(
        userId = userId,
        email = "alice@example.com",
        saltLogin = ByteArray(16) { 1 },
        kdfLogin = KdfParams.DEFAULT,
        saltSLogin = ByteArray(16) { 2 },
        loginVerifier = ByteArray(32) { 3 },
    )

    private fun session(sessionId: String, hash: Int) = Session(
        sessionId = sessionId,
        userId = "user-1",
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
            createDataSource(DbConfig(container.jdbcUrl, container.username, container.password, "test-db"))
                .also(::migrateDatabase)
        }
    }
}
