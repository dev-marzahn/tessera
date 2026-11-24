package dev.marzahn.sankaku.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BookDto(
  @Serializable(with = LenientStringSerializer::class) val id: String,
  val name: String? = null,
  @SerialName("name_en") val nameEn: String? = null,
  val rating: String? = null,
  val status: String? = null,
  @SerialName("post_count") val postCount: Int = 0,
  @SerialName("fav_count") val favCount: Int = 0,
  @SerialName("is_favorited") val isFavorited: Boolean = false,
  val reading: ReadingDto? = null,
  @SerialName("redirect_to_signup") val redirectToSignup: Boolean = false,
  @SerialName("cover_post") val coverPost: PostDto? = null,
  val posts: List<PostDto> = emptyList(),
  val description: String? = null,
  @SerialName("description_en") val descriptionEn: String? = null,
  @SerialName("artist_tags") val artistTags: List<TagDto> = emptyList(),
  @SerialName("genre_tags") val genreTags: List<TagDto> = emptyList(),
  val sequence: Int? = null,
) {
  val title: String get() = nameEn?.takeIf { it.isNotBlank() } ?: name.orEmpty()

  val shownDescription: String? get() = descriptionEn?.takeIf { it.isNotBlank() } ?: description?.takeIf { it.isNotBlank() }
}

@Serializable
data class ReadingDto(
  @SerialName("current_page") val currentPage: Int? = null,
  val sequence: Int? = null,
  val percent: Double? = null,
)

@Serializable
data class SeriesSummaryDto(
  @Serializable(with = LenientStringSerializer::class) val id: String,
  val name: String? = null,
  @SerialName("name_en") val nameEn: String? = null,
  val rating: String? = null,
  val poolCount: Int = 0,
  val firstPool: BookDto? = null,
) {
  val title: String get() = nameEn?.takeIf { it.isNotBlank() } ?: name.orEmpty()
}

@Serializable
data class BookListResponse(
  val series: SeriesList = SeriesList(),
  val pools: Page<BookDto> = Page(emptyList(), Meta()),
) {
  @Serializable
  data class SeriesList(val data: List<SeriesSummaryDto> = emptyList())
}

@Serializable
data class SeriesDto(
  @Serializable(with = LenientStringSerializer::class) val id: String,
  val name: String? = null,
  @SerialName("name_en") val nameEn: String? = null,
  @SerialName("pool_count") val poolCount: Int = 0,
  val books: List<BookDto> = emptyList(),
) {
  val title: String get() = nameEn?.takeIf { it.isNotBlank() } ?: name.orEmpty()
}

@Serializable
internal data class SeriesResponse(val data: Data? = null, val errors: List<GraphQlError> = emptyList()) {
  @Serializable
  data class Data(val series: SeriesDto? = null)
}

@Serializable
internal data class GraphQlError(val message: String? = null)

@Serializable
internal data class BookFavouriteResponse(
  val success: Boolean? = null,
  @SerialName("fav_count") val favCount: Int? = null,
)
