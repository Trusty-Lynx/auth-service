package de.trustylynx.models

import io.ktor.http.HttpStatusCode

/** Fehler, die als JSON an den Client gehen. Beantwortet werden sie in `plugins/StatusPages.kt`. */
open class ApiException(
    val status: HttpStatusCode,
    val code: String,
    val reason: String? = null,
) : RuntimeException(code)

class BadRequest(reason: String) : ApiException(HttpStatusCode.BadRequest, "bad_request", reason)

class Unauthorized : ApiException(HttpStatusCode.Unauthorized, "unauthorized")

class Forbidden : ApiException(HttpStatusCode.Forbidden, "forbidden")

class AlreadyExists : ApiException(HttpStatusCode.Conflict, "already_exists")

class TooManyRequests(val retryAfterSeconds: Long) :
    ApiException(HttpStatusCode.TooManyRequests, "too_many_requests")

/** Ein anderer Service (Key-Service) hat unbrauchbar geantwortet. Wird zu 502. */
class UpstreamException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
