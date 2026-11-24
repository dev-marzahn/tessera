package dev.marzahn.sankaku.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

class RawPage(val posts: List<JsonObject>, val next: String?) {
  val ids: List<String> get() = posts.mapNotNull { (it["id"] as? JsonPrimitive)?.content }
  val created: List<Long> get() = posts.mapNotNull { it.createdAt() }
}

fun JsonObject.createdAt(): Long? =
  ((this["created_at"] as? JsonObject)?.get("s") as? JsonPrimitive)?.content?.toLongOrNull()

fun <T : Comparable<T>> List<T>.descendingBreaks(): Int = zipWithNext().count { (a, b) -> a < b }

fun <T : Comparable<T>> List<T>.inversions(): Int {
  var count = 0
  for (i in indices) for (j in i + 1 until size) if (this[j] > this[i]) count++
  return count
}

fun digest(text: String): String =
  MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(6).joinToString("") { "%02x".format(it) }

fun digestOf(ids: List<String>): String = digest(ids.joinToString(","))

fun hourStart(epochSeconds: Long): Long = epochSeconds - Math.floorMod(epochSeconds, 3600L)

private val BOUND = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

fun dateBound(epochSeconds: Long): String = LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC).format(BOUND)

fun formatAge(seconds: Long): String = when {
  seconds < 3600 -> "${seconds / 60} min"
  seconds < 86400 -> "${seconds / 3600} h"
  else -> "${seconds / 86400} d ${seconds % 86400 / 3600} h"
}

fun cursorShape(cursor: String, ids: Collection<String>): String {
  val encoding = when {
    cursor.all { it.isLetterOrDigit() || it == '-' || it == '_' } -> "base64url"
    cursor.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' } -> "base64"
    else -> "other"
  }
  val bytes = runCatching { Base64.getUrlDecoder().decode(cursor) }.getOrNull()
  val readable = bytes != null && bytes.isNotEmpty() && bytes.count { it in 32..126 } * 10 >= bytes.size * 9
  val text = if (readable) String(bytes!!, Charsets.UTF_8) else null
  val json = text?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
  val holdsId = ids.any { it in cursor || (text != null && it in text) }
  return listOf(
    "encoding=$encoding",
    "bytes=${bytes?.size ?: "-"}",
    "readable text=${yesNo(readable)}",
    "JSON=${yesNo(json != null)}",
    "holds a page id=${yesNo(holdsId)}",
  ).joinToString(", ")
}

fun yesNo(value: Boolean) = if (value) "yes" else "no"

class SeenPost(val id: String, val created: Long, val tags: Set<String>)

fun lateArrivals(head: HeadSnapshot, posts: List<SeenPost>, follows: Set<String>?): String {
  val seen = head.seen.toSet()
  val fresh = posts.filter { it.created >= head.oldest && it.id !in seen }
  val below = fresh.filter { it.created < head.newest }
  val parts = mutableListOf("${fresh.size} new posts in the span it covered, ${below.size} of them below its newest post")
  val before = head.follows?.toSet()
  val added = if (before != null && follows != null) follows - before else null
  parts += when {
    before == null || follows == null -> "follows then or now unknown"
    else -> "follows since: ${added!!.size} added, ${(before - follows).size} removed"
  }
  val fromNewFollows = below.filter { post -> added != null && post.tags.any { it in added } }
  val late = below - fromNewFollows.toSet()
  if (added != null) parts += "${fromNewFollows.size} of those below carry a tag followed since (of their first 5 tags)"
  if (late.isNotEmpty()) {
    val depth = late.maxOf { post -> posts.count { it.id in seen && it.created > post.created } }
    val lags = late.map { head.savedAt - it.created }
    parts += "${late.size} late (no newly followed tag seen): up to $depth of the snapshot's posts above one, " +
      "turned up at least ${formatAge(lags.min())} to ${formatAge(lags.max())} after upload"
  }
  return parts.joinToString(", ")
}
