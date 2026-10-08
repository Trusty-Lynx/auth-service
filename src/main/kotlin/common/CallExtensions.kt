package de.trustylynx.common

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall

const val DEVICE_ID_HEADER = "X-Device-Id"

fun ApplicationCall.deviceIdHeader(): String? = request.headers[DEVICE_ID_HEADER]

fun ApplicationCall.bearerToken(): String? {
    val parts = request.headers[HttpHeaders.Authorization]?.trim()?.split(' ', limit = 2) ?: return null
    if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) return null
    return parts[1].trim().takeIf { it.isNotEmpty() }
}
