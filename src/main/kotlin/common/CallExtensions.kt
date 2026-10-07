package de.trustylynx.common

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin

const val DEVICE_ID_HEADER = "X-Device-Id"

fun ApplicationCall.deviceIdHeader(): String? = request.headers[DEVICE_ID_HEADER]

// TODO: Hinter dem Gateway ist das die IP des Gateways. Dann die vom Gateway gesetzte Client-IP auswerten.
fun ApplicationCall.clientIp(): String = request.origin.remoteHost
