package dev.marzahn.sankaku.api

import dev.marzahn.sankaku.api.dto.FollowedTags
import dev.marzahn.sankaku.api.dto.SuccessResponse
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.api.dto.TagAndWiki
import dev.marzahn.sankaku.api.dto.TagSuggestion
import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TagsApi(private val client: HttpClient) {
  suspend fun autocomplete(prefix: String): List<TagSuggestion> {
    val query = mapOf("tag" to prefix, "show_meta" to "1", "target" to "post", "show_rating" to "true")
    val suggestions = client.apiRequest<List<TagSuggestion>>(
      HttpMethod.Get, API_BASE, "/tags/autosuggestCreating", HeaderSet.POSTS, query,
    )
    return if (prefix.startsWith("-")) suggestions.map { it.excluded() } else suggestions
  }

  suspend fun tagAndWiki(tagName: String): TagAndWiki =
    client.apiRequest(HttpMethod.Get, API_BASE, "/tag-and-wiki/name/${tagName.encodeURLPathPart()}", HeaderSet.V2)

  suspend fun followedTags(page: Int, limit: Int = FOLLOWED_PAGE_SIZE): List<TagDto> {
    val query = mapOf("content_type" to "tags", "limit" to "$limit", "page" to "$page")
    return client.apiRequest<FollowedTags>(HttpMethod.Get, API_BASE, "/users/followings", HeaderSet.V2, query).list
  }

  suspend fun follow(id: String, tagName: String) = following(HttpMethod.Post, id, tagName)

  suspend fun unfollow(id: String, tagName: String) = following(HttpMethod.Delete, id, tagName)

  private suspend fun following(method: HttpMethod, id: String, tagName: String) {
    val body = buildJsonObject {
      put("following_id", id)
      put("following_name", tagName)
      put("type", "tag")
      put("content_type", "tag")
    }
    val response = try {
      client.apiRequest<SuccessResponse>(method, API_BASE, "/users/followings", HeaderSet.V2, body = body)
    } catch (e: ApiError.Http) {
      val already = if (method == HttpMethod.Post) e.status == 400 && ALREADY_FOLLOWED in e.hint else e.status == 404 && NOT_FOLLOWED in e.hint
      if (already) return
      throw e
    }
    if (!response.success) throw ApiError.Http(200, "success=false")
  }

  companion object {
    const val FOLLOWED_PAGE_SIZE = 40
    private const val ALREADY_FOLLOWED = "user__content-already-followed"
    private const val NOT_FOLLOWED = "user__not-follow-content"
  }
}
