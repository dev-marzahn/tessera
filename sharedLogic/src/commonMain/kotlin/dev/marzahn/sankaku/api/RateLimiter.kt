package dev.marzahn.sankaku.api

import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fromHttpToGmtDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class RateLimiter(
  private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
  private val limits: Map<String, Limit> = DEFAULT_LIMITS,
  private val clock: Clock = Clock.System,
  private val defaultPause: Duration = DEFAULT_PAUSE,
) {
  data class Limit(val perSecond: Double, val burst: Int)

  private class Bucket(var tokens: Double, var last: ComparableTimeMark)

  private val mutex = Mutex()
  private val buckets = mutableMapOf<String, Bucket>()
  private val pausedUntil = mutableMapOf<String, ComparableTimeMark>()

  suspend fun acquire(host: String) {
    while (true) {
      val wait = mutex.withLock {
        val now = timeSource.markNow()
        val pause = pausedUntil[host]
        if (pause != null && pause > now) {
          pause - now
        } else {
          pausedUntil.remove(host)
          val limit = limits[host] ?: return
          val bucket = buckets.getOrPut(host) { Bucket(limit.burst.toDouble(), now) }
          val refill = (now - bucket.last).inWholeNanoseconds / 1e9 * limit.perSecond
          bucket.tokens = (bucket.tokens + refill).coerceAtMost(limit.burst.toDouble())
          bucket.last = now
          if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0
            return
          }
          ((1.0 - bucket.tokens) / limit.perSecond).seconds
        }
      }
      delay(wait)
    }
  }

  suspend fun onRateLimited(host: String, retryAfter: String?): Duration {
    val pause = parseRetryAfter(retryAfter) ?: defaultPause
    mutex.withLock {
      val until = timeSource.markNow() + pause
      val running = pausedUntil[host]
      if (running == null || until > running) pausedUntil[host] = until
    }
    return pause
  }

  internal fun parseRetryAfter(value: String?): Duration? = retryAfterPause(value, clock)

  companion object {
    val DEFAULT_PAUSE = 30.seconds

    val MIN_PAUSE = 1.seconds

    val MAX_PAUSE = 10.minutes

    val DEFAULT_LIMITS = mapOf(
      API_HOST to Limit(perSecond = 6.0, burst = 3),
      "s.sankakucomplex.com" to Limit(perSecond = 12.0, burst = 30),
    )
  }
}

internal fun retryAfterPause(value: String?, clock: Clock = Clock.System): Duration? {
  val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val pause = if (DELTA_SECONDS.matches(text)) {
    (text.toDouble() * 1000).let { if (it >= Long.MAX_VALUE) Duration.INFINITE else ceil(it).toLong().milliseconds }
  } else {
    val at = runCatching { text.fromHttpToGmtDate().timestamp }.getOrNull() ?: return null
    (at - clock.now().toEpochMilliseconds()).milliseconds
  }
  return pause.coerceIn(RateLimiter.MIN_PAUSE, RateLimiter.MAX_PAUSE)
}

private val DELTA_SECONDS = Regex("""\d+(\.\d+)?""")

internal fun rateLimiterPlugin(limiter: RateLimiter): ClientPlugin<Unit> = createClientPlugin("RateLimiter") {
  onRequest { request, _ -> limiter.acquire(request.url.host) }
  onResponse { response ->
    if (response.status == HttpStatusCode.TooManyRequests && !isTagLimit(response)) {
      limiter.onRateLimited(response.call.request.url.host, response.headers[HttpHeaders.RetryAfter])
    }
  }
}

private suspend fun isTagLimit(response: HttpResponse): Boolean =
  response.call.request.url.host == API_HOST && tagLimit(response) != null
