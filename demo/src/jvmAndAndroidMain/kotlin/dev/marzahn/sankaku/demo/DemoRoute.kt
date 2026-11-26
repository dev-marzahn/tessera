package dev.marzahn.sankaku.demo

import dev.marzahn.sankaku.api.API_HOST
import dev.marzahn.sankaku.api.IMAGE_HOSTS
import dev.marzahn.sankaku.api.LOGIN_BASE
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.decodeURLPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class DemoNoNetworkException(host: String) : Exception("Demo mode sends nothing to $host")

internal sealed interface DemoRoute {
  data class Search(val query: Parameters) : DemoRoute

  data object Recommended : DemoRoute

  data class Post(val id: String) : DemoRoute

  data class Favourite(val id: String, val on: Boolean) : DemoRoute

  data class Vote(val id: String, val score: Int?) : DemoRoute

  data object Tokens : DemoRoute

  data object Me : DemoRoute

  data class Suggestions(val prefix: String) : DemoRoute

  data class TagWiki(val name: String) : DemoRoute

  data object FollowedTags : DemoRoute

  data class Follow(val name: String, val on: Boolean) : DemoRoute

  data object HiddenTags : DemoRoute

  data class Hide(val name: String) : DemoRoute

  data class Unhide(val ruleId: String) : DemoRoute

  data object Books : DemoRoute

  data object BooksOfPost : DemoRoute

  data class Media(val name: String) : DemoRoute

  data object Unknown : DemoRoute

  companion object {
    private val LOGIN_HOST = Url(LOGIN_BASE).host

    fun of(request: HttpRequestData): DemoRoute {
      val url = request.url
      return when (url.host) {
        API_HOST -> api(request.method, url.encodedPath.trim('/').split('/').map { it.decodeURLPart() }, url.parameters, request.jsonBody())
        LOGIN_HOST -> Tokens
        in IMAGE_HOSTS -> Media(url.encodedPath.substringAfterLast('/'))
        else -> throw DemoNoNetworkException(url.host)
      }
    }

    private fun api(method: HttpMethod, path: List<String>, query: Parameters, body: JsonObject?): DemoRoute {
      val get = method == HttpMethod.Get
      val removes = method == HttpMethod.Delete
      return when {
        get && path == listOf("v2", "posts", "keyset") -> Search(query)
        get && path == listOf("v2", "posts") -> Recommended
        get && path.size == 2 && path[0] == "posts" -> Post(path[1])
        path.size == 3 && path[0] == "posts" && path[2] == "favorite" -> Favourite(path[1], on = !removes)
        path.size == 3 && path[0] == "posts" && path[2] == "vote" -> Vote(path[1], body.string("score")?.toIntOrNull())
        path == listOf("auth", "token") || path == listOf("sso", "token-exchange") -> Tokens
        path == listOf("users", "me") -> Me
        path == listOf("tags", "autosuggestCreating") -> Suggestions(query["tag"].orEmpty().removePrefix("-"))
        path.size == 3 && path[0] == "tag-and-wiki" -> TagWiki(path[2])
        path == listOf("users", "followings") && get -> FollowedTags
        path == listOf("users", "followings") -> Follow(body.string("following_name").orEmpty(), on = !removes)
        path == listOf("users", "blacklist") && get -> HiddenTags
        path == listOf("users", "blacklist") -> Hide(body?.get("tags")?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty())
        path.size == 3 && path[0] == "users" && path[1] == "blacklist" && removes -> Unhide(path[2])
        get && path == listOf("poolseriesv2") -> Books
        get && path.size == 3 && path[0] == "post" && path[2] == "pools" -> BooksOfPost
        else -> Unknown
      }
    }

    private fun HttpRequestData.jsonBody(): JsonObject? =
      (body as? TextContent)?.text?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }

    private fun JsonObject?.string(key: String): String? = this?.get(key)?.jsonPrimitive?.contentOrNull
  }
}
