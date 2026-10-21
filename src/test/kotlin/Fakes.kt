package de.trustylynx

import de.trustylynx.clients.KeyServiceClient
import de.trustylynx.common.AlreadyExists
import de.trustylynx.common.UpstreamException
import de.trustylynx.common.hmacSha256
import de.trustylynx.domain.Session
import de.trustylynx.domain.User
import de.trustylynx.persistence.SessionRepository
import de.trustylynx.persistence.Transactions
import de.trustylynx.persistence.UserRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

class MutableClock(private var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

class InMemoryUserRepository : UserRepository {
    val users = ConcurrentHashMap<String, User>()

    override suspend fun create(user: User) {
        if (users.values.any { it.email == user.email }) throw AlreadyExists()
        if (users.putIfAbsent(user.userId, user) != null) throw AlreadyExists()
    }

    override suspend fun findByEmail(email: String): User? = users.values.firstOrNull { it.email == email }
}

class InMemorySessionRepository : SessionRepository {
    val sessions = ConcurrentHashMap<String, Session>()

    override suspend fun create(session: Session) {
        sessions[session.sessionId] = session
    }

    override suspend fun findByTokenHash(tokenHash: ByteArray): Session? =
        sessions.values.firstOrNull { it.tokenHash.contentEquals(tokenHash) }

    override suspend fun delete(sessionId: String): Boolean = sessions.remove(sessionId) != null
}

class InMemoryTransactions(
    private val users: InMemoryUserRepository,
    private val sessions: InMemorySessionRepository,
) : Transactions {
    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        val usersBefore = HashMap(users.users)
        val sessionsBefore = HashMap(sessions.sessions)
        try {
            return block()
        } catch (e: Throwable) {
            users.users.apply { clear(); putAll(usersBefore) }
            sessions.sessions.apply { clear(); putAll(sessionsBefore) }
            throw e
        }
    }
}

class FakeKeyServiceClient : KeyServiceClient {
    var failCreateUserKeys = false
    val createdUserKeys = mutableListOf<String>()

    override suspend fun pepperHmac(userId: String, loginKey: ByteArray): ByteArray = hmacSha256(PEPPER, loginKey)

    override suspend fun createUserKeys(userId: String) {
        if (failCreateUserKeys) throw UpstreamException("key-service create-user-keys answered 500")
        createdUserKeys += userId
    }

    private companion object {
        val PEPPER = ByteArray(32) { 7 }
    }
}
