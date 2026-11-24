package dev.marzahn.sankaku.api

import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.api.dto.BookFavouriteResponse
import dev.marzahn.sankaku.api.dto.BookListResponse
import dev.marzahn.sankaku.api.dto.Page
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.SeriesDto
import dev.marzahn.sankaku.api.dto.SeriesResponse
import dev.marzahn.sankaku.api.dto.SuccessResponse
import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class BookFavouriteResult(val favCount: Int?)

class BooksApi(private val client: HttpClient) {
  suspend fun list(tags: List<String>, page: Int = 1, limit: Int = PAGE_SIZE): BookListResponse {
    val query = mapOf(
      "tags" to tags.joinToString(" "),
      "page" to page.toString(),
      "limit" to limit.toString(),
      "filledPools" to "true",
      "includes[]" to "pools",
      "exceptStatuses[]" to "deleted",
    )
    return client.apiRequest(HttpMethod.Get, API_BASE, "/poolseriesv2", HeaderSet.POSTS, query)
  }

  suspend fun pages(bookId: String): BookDto = client.apiRequest(
    HttpMethod.Get, API_BASE, "/pools/$bookId", HeaderSet.POSTS, mapOf("exceptStatuses[]" to "deleted"),
  )

  suspend fun firstPages(bookId: String, limit: Int = FIRST_PAGES): List<PostDto> {
    val query = mapOf("tags" to "pool:$bookId", "limit" to limit.toString())
    return client.apiRequest<Page<PostDto>>(HttpMethod.Get, API_BASE, "/v2/posts/keyset", HeaderSet.POSTS, query).data
  }

  suspend fun booksOf(postId: String): List<BookDto> =
    client.apiRequest(HttpMethod.Get, API_BASE, "/post/$postId/pools", HeaderSet.POSTS)

  suspend fun series(seriesId: String): SeriesDto {
    val body = buildJsonObject {
      put("query", SERIES_QUERY)
      putJsonObject("variables") { put("id", seriesId) }
    }
    val response = client.apiRequest<SeriesResponse>(HttpMethod.Post, API_BASE, "/graphql", HeaderSet.POSTS, body = body, aboutId = seriesId)
    return response.data?.series ?: throw ApiError.Http(200, if (response.errors.isEmpty()) "no series" else "graphql error")
  }

  suspend fun saveReading(bookId: String, page: Int) {
    val body = buildJsonObject { put("sequence", page) }
    val response = client.apiRequest<SuccessResponse>(HttpMethod.Put, API_BASE, "/pools/$bookId/reading", HeaderSet.NONE, body = body)
    if (!response.success) throw ApiError.Http(200, "success=false")
  }

  suspend fun clearReading(bookId: String) {
    val body = buildJsonObject {
      putJsonArray("pool_ids") { add(bookId) }
      putJsonArray("series_ids") {}
    }
    val response = client.apiRequest<SuccessResponse>(HttpMethod.Patch, API_BASE, "/pools/readings/destroy", HeaderSet.NONE, body = body, aboutId = bookId)
    if (!response.success) throw ApiError.Http(200, "success=false")
  }

  suspend fun favourite(bookId: String, on: Boolean): BookFavouriteResult {
    val method = if (on) HttpMethod.Post else HttpMethod.Delete
    val response = client.apiRequest<BookFavouriteResponse>(method, API_BASE, "/pools/$bookId/favorite", HeaderSet.NONE)
    if (response.success == false) throw ApiError.Http(200, "success=false")
    return BookFavouriteResult(response.favCount)
  }

  companion object {
    const val PAGE_SIZE = 20

    const val FIRST_PAGES = 3

    private val COVER_FIELDS = """
      id md5 rating status file_type: fileType
      preview_url: previewUrl preview_width: previewWidth preview_height: previewHeight
      sample_url: sampleUrl sample_width: sampleWidth sample_height: sampleHeight
      file_url: fileUrl width height
    """.trimIndent()

    internal val SERIES_QUERY = """
      query Series(${'$'}id: StringOrInt!) {
        series: seriesV2(id: ${'$'}id) {
          id name name_en: nameEn pool_count: poolCount
          books: pools {
            id name name_en: nameEn post_count: postCount rating status
            is_favorited: isFavorited fav_count: favCount sequence: sequenceV2(seriesId: ${'$'}id)
            reading { current_page: currentPage }
            cover_post: coverPost { $COVER_FIELDS }
          }
        }
      }
    """.trimIndent()
  }
}
