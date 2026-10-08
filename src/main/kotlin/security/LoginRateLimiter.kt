package de.trustylynx.security

import de.trustylynx.common.TooManyRequests
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class LoginRateLimiter(
    private val clock: Clock,
    private val maxFailures: Int = 5,
    private val window: Duration = Duration.ofSeconds(60),
) {
    private val failures = ConcurrentHashMap<String, List<Instant>>()

    fun checkAllowed(email: String) {
        val now = clock.instant()
        var retryAfterSeconds = 0L
        failures.computeIfPresent(email) { _, times ->
            val recent = times.withinWindow(now)
            if (recent.size >= maxFailures) {
                val millis = Duration.between(now, recent.first().plus(window)).toMillis()
                retryAfterSeconds = maxOf(1, (millis + 999) / 1000)
            }
            recent.ifEmpty { null }
        }
        if (retryAfterSeconds > 0) throw TooManyRequests(retryAfterSeconds)
    }

    fun recordFailure(email: String) {
        val now = clock.instant()
        failures.merge(email, listOf(now)) { old, new -> (old.withinWindow(now) + new).takeLast(maxFailures) }
        if (failures.size > MAX_TRACKED_EMAILS) {
            failures.entries.removeIf { (_, times) -> times.withinWindow(now).isEmpty() }
        }
    }

    fun reset(email: String) {
        failures.remove(email)
    }

    private fun List<Instant>.withinWindow(now: Instant): List<Instant> {
        val start = now.minus(window)
        return filter { it.isAfter(start) }
    }

    private companion object {
        const val MAX_TRACKED_EMAILS = 10_000
    }
}
