package dev.marzahn.sankaku.api

import dev.marzahn.sankaku.api.dto.FavouriteResponse
import dev.marzahn.sankaku.api.dto.Page
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.VoteResponse
import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class FavouriteResult(val postId: String)

data class VoteResult(val voteCount: Int?)

class PostsApi(private val client: HttpClient) {
  suspend fun keyset(tags: List<String>, next: String? = null, page: Int = 1, limit: Int = PAGE_SIZE): Page<PostDto> {
    val (feeds, searched) = tags.partition { Feed.of(it) != null }
    val query = buildMap {
      put("limit", limit.toString())
      put("page", page.toString())
      put("hide_posts_in_books", "in-larger-tags")
      if (searched.isNotEmpty()) put("tags", searched.joinToString(" "))
      feeds.forEach { put(Feed.of(it)!!.flag, "true") }
      next?.let { put("next", it) }
    }
    return client.apiRequest(HttpMethod.Get, API_BASE, "/v2/posts/keyset", HeaderSet.POSTS, query)
  }

  suspend fun byId(id: String): PostDto = byIdOrNull(id) ?: throw ApiError.Http(404, "post not found")

  suspend fun full(id: String): PostDto = client.apiRequest(HttpMethod.Get, API_BASE, "/posts/$id", HeaderSet.POSTS)

  suspend fun recommended(postId: String, tags: List<String> = emptyList(), limit: Int = RECOMMENDED_LIMIT): List<PostDto> {
    val query = mapOf(
      "tags" to (listOf("recommended_for_post:$postId") + tags).joinToString(" "),
      "limit" to limit.toString(),
      "page" to "1",
    )
    return client.apiRequest(HttpMethod.Get, API_BASE, "/v2/posts", HeaderSet.POSTS, query)
  }

  suspend fun children(parentId: String, tags: List<String> = emptyList(), limit: Int = CHILDREN_LIMIT): List<PostDto> =
    keyset(listOf("parent:$parentId") + tags, limit = limit).data

  suspend fun byIdOrNull(id: String, tags: List<String> = emptyList()): PostDto? =
    keyset(listOf("id:$id") + tags, limit = 1).data.firstOrNull()

  suspend fun favourite(id: String): FavouriteResult = favouriteCall(HttpMethod.Post, id)

  suspend fun unfavourite(id: String): FavouriteResult = favouriteCall(HttpMethod.Delete, id)

  private suspend fun favouriteCall(method: HttpMethod, id: String): FavouriteResult {
    val response = client.apiRequest<FavouriteResponse>(method, API_BASE, "/posts/$id/favorite", HeaderSet.NONE)
    return FavouriteResult(postId = response.postId ?: id)
  }

  suspend fun vote(id: String, score: Int): VoteResult =
    voteCall(HttpMethod.Put, id, buildJsonObject { put("score", score) })

  suspend fun unvote(id: String): VoteResult = voteCall(HttpMethod.Delete, id, null)

  private suspend fun voteCall(method: HttpMethod, id: String, body: JsonObject?): VoteResult {
    val response = client.apiRequest<VoteResponse>(method, API_BASE, "/posts/$id/vote", HeaderSet.V2, body = body)
    if (response.success == false) throw ApiError.Http(200, "success=false")
    return VoteResult(voteCount = response.voteCount)
  }

  companion object {
    const val PAGE_SIZE = 30

    const val RECOMMENDED_LIMIT = 20

    const val CHILDREN_LIMIT = 20
  }
}
