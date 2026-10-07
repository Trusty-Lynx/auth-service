package de.trustylynx

import io.ktor.http.HttpStatusCode

sealed class ApiException(
    val status: HttpStatusCode,
    val code: String,
) : RuntimeException(code, null, false, false)

class BadRequest : ApiException(HttpStatusCode.BadRequest, "bad_request")

class Unauthorized : ApiException(HttpStatusCode.Unauthorized, "unauthorized")

class Forbidden : ApiException(HttpStatusCode.Forbidden, "forbidden")

class NotFound : ApiException(HttpStatusCode.NotFound, "not_found")

class AlreadyExists : ApiException(HttpStatusCode.Conflict, "already_exists")

class TooManyRequests(val retryAfterSeconds: Long) :
    ApiException(HttpStatusCode.TooManyRequests, "too_many_requests")


class UpstreamException(message: String) : RuntimeException(message)