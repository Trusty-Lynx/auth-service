package de.trustylynx

import de.trustylynx.clients.KeyServiceClient
import de.trustylynx.models.AlreadyExists
import de.trustylynx.models.Session
import de.trustylynx.models.UpstreamException
import de.trustylynx.models.User
import de.trustylynx.repositories.SessionRepository
import de.trustylynx.repositories.UserRepository
import de.trustylynx.security.hmacSha256
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

class InMemoryUserRepository(private val sessions: InMemorySessionRepository) : UserRepository {
    val users = ConcurrentHashMap<String, User>()

    override suspend fun create(user: User, firstSession: Session) {
        if (users.values.any { it.email == user.email }) throw AlreadyExists()
        if (users.putIfAbsent(user.userId, user) != null) throw AlreadyExists()
        sessions.create(firstSession)
    }

    override suspend fun findByEmail(email: String): User? = users.values.firstOrNull { it.email == email }

    override suspend fun markTotpStepUsed(userId: String, step: Long): Boolean = updateIf(userId) { user ->
        val lastStep = user.totpLastStep
        if (user.totpSecret != null && (lastStep == null || lastStep < step)) user.withTotp(user.totpSecret, step) else null
    }

    override suspend fun enableTotp(userId: String, secret: ByteArray, step: Long): Boolean = updateIf(userId) { user ->
        if (user.totpSecret == null) user.withTotp(secret, step) else null
    }

    fun removeTotp(userId: String) {
        users.computeIfPresent(userId) { _, user -> user.withTotp(null, null) }
    }

    // atomic like the conditional UPDATE in postgres. change returns the new user, or null to leave it alone
    private fun updateIf(userId: String, change: (User) -> User?): Boolean {
        var updated = false
        users.computeIfPresent(userId) { _, user ->
            val changed = change(user)
            updated = changed != null
            changed ?: user
        }
        return updated
    }

    private fun User.withTotp(secret: ByteArray?, lastStep: Long?) =
        User(userId, email, saltLogin, kdfLogin, saltSLogin, loginVerifier, totpSecret = secret, totpLastStep = lastStep)
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
