package dev.marzahn.sankaku.demo

import dev.marzahn.sankaku.api.FILE_HOST
import dev.marzahn.sankaku.api.dto.FollowedTags
import dev.marzahn.sankaku.api.dto.Meta
import dev.marzahn.sankaku.api.dto.Page
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.TagAndWiki
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.api.dto.TagSuggestion
import dev.marzahn.sankaku.api.dto.UserSummary
import dev.marzahn.sankaku.api.dto.WikiDto
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.atomic.AtomicReference

private val DemoJson = Json { explicitNulls = false }

internal class DemoSankaku(private val media: DemoMedia) {
  private data class Account(
    val favourites: Set<String> = emptySet(),
    val votes: Map<String, Int> = emptyMap(),
    val follows: Set<String> = emptySet(),
    val hidden: Map<String, String> = emptyMap(),
  )

  private val account = AtomicReference(Account())

  val engine: HttpClientEngine = MockEngine { request -> answer(DemoRoute.of(request)) }

  private fun MockRequestHandleScope.answer(route: DemoRoute): HttpResponseData = when (route) {
    is DemoRoute.Search -> json(encode(Page.serializer(PostDto.serializer()), search(route.query)))
    DemoRoute.Recommended -> json(encode(ListSerializer(PostDto.serializer()), emptyList()))
    is DemoRoute.Post -> DemoCatalogue.posts.firstOrNull { it.id == route.id }?.let { json(encode(PostDto.serializer(), dto(it))) } ?: notFound()
    is DemoRoute.Favourite -> {
      update { it.copy(favourites = if (route.on) it.favourites + route.id else it.favourites - route.id) }
      json(DemoReplies.favourite(route.id))
    }
    is DemoRoute.Vote -> {
      update { it.copy(votes = if (route.score == null) it.votes - route.id else it.votes + (route.id to route.score)) }
      json(DemoReplies.vote(voted = route.score != null))
    }
    DemoRoute.Tokens -> json(DemoReplies.tokens(USER))
    DemoRoute.Me -> json(DemoReplies.me(USER))
    is DemoRoute.Suggestions -> json(encode(ListSerializer(TagSuggestion.serializer()), suggestions(route.prefix)))
    is DemoRoute.TagWiki -> DemoCatalogue.tags.firstOrNull { it.searchName == route.name }
      ?.let { json(encode(TagAndWiki.serializer(), TagAndWiki(it.followed(), WikiDto(it.name, WIKI)))) }
      ?: notFound()
    DemoRoute.FollowedTags -> json(encode(FollowedTags.serializer(), FollowedTags(DemoCatalogue.tags.filter { it.searchName in account.get().follows }.map { it.followed() })))
    is DemoRoute.Follow -> {
      update { it.copy(follows = if (route.on) it.follows + route.name else it.follows - route.name) }
      json(DemoReplies.SUCCESS)
    }
    DemoRoute.HiddenTags -> json(DemoReplies.hiddenCount(account.get().hidden.size))
    is DemoRoute.Hide -> {
      val ruleId = "demo-rule-${route.name}"
      json(DemoReplies.hidden(ruleId, update { it.copy(hidden = it.hidden + (ruleId to route.name)) }.hidden.size))
    }
    is DemoRoute.Unhide -> json(DemoReplies.hiddenCount(update { it.copy(hidden = it.hidden - route.ruleId) }.hidden.size))
    DemoRoute.Books -> json("{}")
    DemoRoute.BooksOfPost -> json("[]")
    is DemoRoute.Media -> media.find(route.name)?.let { respond(it, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, mediaType(route.name))) } ?: notFound()
    DemoRoute.Unknown -> notFound()
  }

  private fun update(change: (Account) -> Account): Account = account.updateAndGet(change)

  private fun search(query: Parameters): Page<PostDto> {
    val terms = query["tags"].orEmpty().split(' ').filter { it.isNotBlank() }
    val now = account.get()
    val matching = DemoCatalogue.posts.filter { post ->
      terms.all { post.matches(it, now) } &&
        post.tagNames.none { it in now.hidden.values } &&
        (query["is_following"] != "true" || post.tagNames.any { it in now.follows })
    }
    val from = query["next"]?.removePrefix(CURSOR)?.toIntOrNull() ?: 0
    val limit = query["limit"]?.toIntOrNull() ?: matching.size
    val next = (from + limit).takeIf { it < matching.size }?.let { "$CURSOR$it" }
    return Page(matching.drop(from).take(limit).map(::dto), Meta(next))
  }

  private fun DemoPost.matches(term: String, now: Account): Boolean = when {
    term.startsWith("-") -> !matches(term.drop(1), now)
    term.startsWith("rating:") -> term.substringAfter(':') == DemoCatalogue.RATING
    term.startsWith("id:") -> term.substringAfter(':') == id
    term.startsWith("fav:") -> id in now.favourites
    term.startsWith("parent:") -> false
    ':' in term -> true
    else -> term in tagNames
  }

  private fun dto(post: DemoPost): PostDto {
    val now = account.get()
    val favourited = post.id in now.favourites
    val vote = now.votes[post.id]
    val file = served(post.fileName)
    return PostDto(
      id = post.id,
      md5 = post.fakeMd5(),
      rating = DemoCatalogue.RATING,
      status = "active",
      fileType = post.fileType,
      fileExt = post.extension,
      previewUrl = served(post.previewName),
      previewWidth = PREVIEW_WIDTH,
      previewHeight = post.height * PREVIEW_WIDTH / post.width,
      sampleUrl = if (post.isVideo) media.playable(post) else file,
      sampleWidth = post.width,
      sampleHeight = post.height,
      fileUrl = file,
      fileWidth = post.width,
      fileHeight = post.height,
      videoDuration = post.videoSeconds,
      isFavorited = favourited,
      favCount = if (favourited) 1 else 0,
      voteCount = if (vote != null) 1 else 0,
      totalScore = vote ?: 0,
      userVote = vote,
      tags = post.tags.map { it.followed() },
      tagNames = post.tagNames,
      createdAt = post.postedAt,
    )
  }

  private fun TagDto.followed() = copy(isFollowing = searchName in account.get().follows)

  private fun suggestions(prefix: String): List<TagSuggestion> =
    DemoCatalogue.tags.filter { it.searchName.startsWith(prefix) }.take(SUGGESTIONS).map { tag ->
      TagSuggestion(tagName = tag.searchName, name = tag.name, type = tag.type, postCount = tag.postCount, rating = DemoCatalogue.RATING)
    }

  private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

  private fun MockRequestHandleScope.notFound(): HttpResponseData = json(DemoReplies.FAILURE, HttpStatusCode.NotFound)

  private fun <T> encode(serializer: KSerializer<T>, value: T): String = DemoJson.encodeToString(serializer, value)

  private fun mediaType(name: String): String = when (name.substringAfterLast('.')) {
    "gif" -> ContentType.Image.GIF
    "mp4" -> ContentType.Video.MP4
    else -> ContentType.Image.JPEG
  }.toString()

  private companion object {
    const val CURSOR = "demo-after-"
    const val PREVIEW_WIDTH = 300
    const val SUGGESTIONS = 10
    const val NEVER_EXPIRES = 4_102_444_800L
    const val WIKI = "A tag on Tessera's demo posts."
    val USER = UserSummary(id = "demo-user", name = "Demo")

    fun served(name: String) = "https://$FILE_HOST/data/demo/$name?e=$NEVER_EXPIRES"
  }
}

private object DemoReplies {
  const val SUCCESS = """{"success":true}"""
  const val FAILURE = """{"success":false}"""
  private const val TOKEN_SECONDS = 30L * 24 * 60 * 60

  fun tokens(user: UserSummary): String = buildJsonObject {
    put("success", true)
    put("access_token", "demo-access-token")
    put("refresh_token", "demo-refresh-token")
    put("access_token_ttl", TOKEN_SECONDS)
    put("refresh_token_ttl", TOKEN_SECONDS)
    put("current_user", DemoJson.encodeToJsonElement(UserSummary.serializer(), user))
  }.toString()

  fun me(user: UserSummary): String = buildJsonObject { put("user", DemoJson.encodeToJsonElement(UserSummary.serializer(), user)) }.toString()

  fun favourite(postId: String): String = buildJsonObject {
    put("success", true)
    put("post_id", postId)
  }.toString()

  fun vote(voted: Boolean): String = buildJsonObject {
    put("success", true)
    put("vote_count", if (voted) 1 else 0)
  }.toString()

  fun hidden(ruleId: String, count: Int): String = buildJsonObject {
    putJsonObject("new_blacklist") { put("id", ruleId) }
    putJsonObject("count") { put("used_rules", count) }
  }.toString()

  fun hiddenCount(count: Int): String = buildJsonObject { putJsonObject("count") { put("used_rules", count) } }.toString()
}
