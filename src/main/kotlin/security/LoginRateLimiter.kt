package de.trustylynx.security

import de.trustylynx.common.TooManyRequests
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Sperrt eine E-Mail nach [maxFailures] fehlgeschlagenen Logins innerhalb von [window]. Liegt nur im Speicher. */
class LoginRateLimiter(
    private val clock: Clock,
    private val maxFailures: Int = 5,
    private val window: Duration = Duration.ofSeconds(60),
) {
    // Pro E-Mail die Zeitpunkte der letzten Fehlversuche, ältester zuerst.
    // Alle Methoden sind @Synchronized, weil mehrere Requests gleichzeitig darauf zugreifen.
    private val failures = HashMap<String, List<Instant>>()

    @Synchronized
    fun checkAllowed(email: String) {
        val now = clock.instant()
        val recent = recentFailures(email, now)
        if (recent.size >= maxFailures) {
            val millisLeft = Duration.between(now, recent.first().plus(window)).toMillis()
            val secondsLeft = (millisLeft + 999) / 1000 // aufgerundet auf volle Sekunden
            throw TooManyRequests(retryAfterSeconds = maxOf(1, secondsLeft))
        }
    }

    @Synchronized
    fun recordFailure(email: String) {
        val now = clock.instant()
        failures[email] = (recentFailures(email, now) + now).takeLast(maxFailures)

        // Damit die Map nicht unbegrenzt wächst: E-Mails ohne aktuelle Fehlversuche wieder entfernen.
        if (failures.size > MAX_TRACKED_EMAILS) {
            failures.keys.removeIf { recentFailures(it, now).isEmpty() }
        }
    }

    @Synchronized
    fun reset(email: String) {
        failures.remove(email)
    }

    private fun recentFailures(email: String, now: Instant): List<Instant> {
        val windowStart = now.minus(window)
        return failures[email].orEmpty().filter { it.isAfter(windowStart) }
    }

    private companion object {
        const val MAX_TRACKED_EMAILS = 10_000
    }
}
