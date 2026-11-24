package dev.marzahn.sankaku.cli

import dev.marzahn.sankaku.api.AuthApi
import dev.marzahn.sankaku.api.PostsApi
import dev.marzahn.sankaku.api.RateLimiter
import dev.marzahn.sankaku.api.TagsApi
import dev.marzahn.sankaku.api.UsersApi
import dev.marzahn.sankaku.api.createAuthClient
import dev.marzahn.sankaku.api.createEngine
import dev.marzahn.sankaku.api.createMainClient
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.TagType
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.auth.SignInResult
import dev.marzahn.sankaku.log.RequestLog
import dev.marzahn.sankaku.net.ConnectivityState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class BacklogDiscovery(private val env: Map<String, String>) {
  private val engine = createEngine()
  private val log = RequestLog()
  private val limiter = RateLimiter()
  private val connectivity = ConnectivityState()
  private val session = AuthSession(AuthApi(createAuthClient(engine, log, limiter, connectivity)), InMemoryTokenStore())
  private val client = createMainClient(engine, log, limiter, connectivity, session)
  private val posts = PostsApi(client)
  private val tags = TagsApi(client)
  private val users = UsersApi(client)

  suspend fun run() {
    val login = env["SANKAKU_LOGIN"].orEmpty()
    val password = env["SANKAKU_PASSWORD"].orEmpty()
    val signedIn = login.isNotBlank() && password.isNotBlank() && try {
      session.signIn(login, password).also {
        println("Sign-in: ${if (it == SignInResult.Ok) "ok" else it::class.simpleName}")
      } == SignInResult.Ok
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      println("Sign-in failed: ${e::class.simpleName}")
      false
    }
    for ((step, run) in listOf("d29" to ::postCounts, "d30" to ::favouriteOrder)) {
      if (step == "d30" && !signedIn) {
        println("d30 skipped: not signed in")
        continue
      }
      try {
        run()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        println("$step failed: ${e::class.simpleName}")
      }
    }
  }

  private suspend fun postCounts() {
    println("d29 post_count of a one-tag search's tag, in its first page's tags[] (${PostsApi.PAGE_SIZE} posts)")
    val picked = listOf("scenery" to TagType.General) + busyTags()
    picked.forEachIndexed { n, (tag, type) ->
      val wiki = runCatching { tags.tagAndWiki(tag).tag.postCount }.getOrNull()
      val label = if (tag == "scenery") "scenery" else "tag ${n + 1}"
      println("  $label ($type, ${bucket(wiki)} on /tag-and-wiki)")
      for (order in listOf(null, "order:popularity")) {
        for (rating in listOf(null, "rating:s")) {
          val page = posts.keyset(listOfNotNull(order, tag, rating)).data.filterNot { it.redirectToSignup }
          val carrying = page.mapNotNull { post -> post.tags.firstOrNull { it.searchName.equals(tag, ignoreCase = true) } }
          val counts = carrying.mapNotNull { it.postCount }.distinct()
          val matches = when {
            counts.isEmpty() || wiki == null -> "-"
            counts.all { it == wiki } -> "yes"
            else -> "no (${counts.joinToString { off(it, wiki) }})"
          }
          val missing = page.filter { post -> post.tags.none { it.searchName.equals(tag, ignoreCase = true) } }
          val missingTypes = missing.flatMap { post -> post.tags.map { it.category } }.groupingBy { it }.eachCount()
          println(
            "    ${order ?: "default order"}${rating?.let { ", $it" }.orEmpty()}: posts=${page.size}, " +
              "carry it=${carrying.size}, with post_count=${carrying.count { it.postCount != null }}, " +
              "distinct counts=${counts.size}, = tag-and-wiki: $matches" +
              (if (missing.isEmpty()) "" else "; the 5 tags where it's missing: $missingTypes"),
          )
        }
      }
    }
  }

  private suspend fun busyTags(): List<Pair<String, TagType>> {
    val popular = posts.keyset(listOf("order:popularity", "rating:s"), limit = 20).data.filterNot { it.redirectToSignup }
    val found = linkedMapOf<String, TagType>()
    for (post in popular) {
      val full = runCatching { posts.full(post.id) }.getOrNull() ?: continue
      for (type in listOf(TagType.Artist, TagType.Copyright, TagType.Character)) {
        if (found.values.count { it == type } >= PER_TYPE) continue
        full.tags.firstOrNull { it.category == type && it.searchName !in found }?.let { found[it.searchName] = type }
      }
      if (found.size >= PER_TYPE * 3) break
    }
    return found.entries.map { it.key to it.value }
  }

  private suspend fun favouriteOrder() {
    val name = users.me().name
    val fav = "fav:$name"
    println("d30 fav:<name> order")
    val listed = pages(listOf(fav), 3)
    val dates = listed.mapNotNull { it.createdAt }
    val outOfOrder = dates.zipWithNext().count { (a, b) -> b > a }
    println("  read: posts=${listed.size}, with created_at=${dates.size}, uploaded later than the one before=$outOfOrder")
    if (env["SANKAKU_TRY_FAVOURITE"] == "false") {
      println("  write skipped: SANKAKU_TRY_FAVOURITE=false")
      return
    }
    val new = firstNotFavourited(listOf("rating:s")) ?: return println("  write skipped: no new post to favourite")
    val old = OLD_SEARCHES.firstNotNullOfOrNull { runCatching { firstNotFavourited(it) }.getOrNull() }
      ?: return println("  write skipped: no 2014 post to favourite")
    println("  write: favourite a post uploaded ${ago(new)}, then one uploaded ${ago(old)}")
    val favourited = mutableListOf<Pair<String, String>>()
    try {
      posts.favourite(new.id)
      favourited += "the new post" to new.id
      delay(2.seconds)
      posts.favourite(old.id)
      favourited += "the 2014 post" to old.id
      for (search in listOf(listOf(fav), listOf(fav, "rating:s"))) {
        var where = positions(search, old.id, new.id)
        var tries = 1
        while ((where.first == null || where.second == null) && tries < 6) {
          delay(5.seconds)
          where = positions(search, old.id, new.id)
          tries++
        }
        println(
          "  ${if (search.size == 1) "fav:<name>" else "fav:<name> rating:s"}: the 2014 post (favourited last) at " +
            "${where.first?.plus(1) ?: "not in the first pages"}, the new one at ${where.second?.plus(1) ?: "not in the first pages"} " +
            "(after $tries reads)",
        )
      }
    } finally {
      withContext(NonCancellable) {
        for ((which, id) in favourited) {
          val undone = runCatching { posts.unfavourite(id) }.isSuccess
          println(if (undone) "  $which unfavourited" else "  unfavouriting $which failed: unfavourite it by hand")
        }
      }
    }
  }

  private suspend fun positions(search: List<String>, old: String, new: String): Pair<Int?, Int?> {
    val listed = runCatching { pages(search, 2).map { it.id } }.getOrDefault(emptyList())
    return listed.indexOf(old).takeIf { it >= 0 } to listed.indexOf(new).takeIf { it >= 0 }
  }

  private suspend fun pages(search: List<String>, count: Int): List<PostDto> {
    val all = mutableListOf<PostDto>()
    var next: String? = null
    for (page in 1..count) {
      val result = posts.keyset(search, next = next, page = page)
      all += result.data
      next = result.meta.next ?: break
    }
    return all
  }

  private suspend fun firstNotFavourited(search: List<String>): PostDto? =
    posts.keyset(search, limit = 20).data.firstOrNull { !it.redirectToSignup && !it.isFavorited }

  private fun ago(post: PostDto): String {
    val created = post.createdAt ?: return "at an unknown time"
    val days = (kotlin.time.Clock.System.now() - created).inWholeDays
    return if (days < 2) "within 2 days" else "$days days ago"
  }

  fun close() {
    client.close()
  }

  private companion object {
    const val PER_TYPE = 2

    val OLD_SEARCHES = listOf(
      listOf("scenery", "rating:s", "date:2014-06-01..2014-07-01"),
      listOf("scenery", "rating:s", "date:2014-06-01..2014-06-02"),
      listOf("sky", "rating:s", "date:2014-06-01..2014-06-02"),
    )

    fun bucket(count: Int?): String = when {
      count == null -> "no post_count"
      count < 100 -> "<100"
      count < 1_000 -> "100–999"
      count < 10_000 -> "1k–10k"
      count < 100_000 -> "10k–100k"
      else -> "100k+"
    }

    fun off(count: Int, wiki: Int): String = "%+.2f%%".format((count - wiki) * 100.0 / wiki.coerceAtLeast(1))
  }
}
