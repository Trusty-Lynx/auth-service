package de.trustylynx.common

import java.security.SecureRandom

private val secureRandom = SecureRandom()

fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)
