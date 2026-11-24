package dev.marzahn.sankaku.data

import io.ktor.http.Url
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

fun signedUrlExpiresAt(url: String): Instant? =
  runCatching { Url(url) }.getOrNull()?.parameters?.get("e")?.toLongOrNull()?.let(Instant::fromEpochSeconds)

fun isSignedUrlUsable(url: String, now: Instant): Boolean =
  signedUrlExpiresAt(url)?.let { it > now + 1.minutes } ?: false
