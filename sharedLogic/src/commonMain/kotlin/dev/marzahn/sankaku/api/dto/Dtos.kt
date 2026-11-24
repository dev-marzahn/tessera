package dev.marzahn.sankaku.api.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Instant

@Serializable
data class PostDto(
  @Serializable(with = LenientStringSerializer::class) val id: String,
  val md5: String,
  val author: Author? = null,
  val rating: String,
  val status: String,
  @SerialName("file_type") val fileType: String? = null,
  @SerialName("file_ext") val fileExt: String? = null,
  @SerialName("preview_url") val previewUrl: String? = null,
  @SerialName("preview_width") val previewWidth: Int? = null,
  @SerialName("preview_height") val previewHeight: Int? = null,
  @SerialName("sample_url") val sampleUrl: String? = null,
  @SerialName("sample_width") val sampleWidth: Int? = null,
  @SerialName("sample_height") val sampleHeight: Int? = null,
  @SerialName("file_url") val fileUrl: String? = null,
  @SerialName("width") val fileWidth: Int? = null,
  @SerialName("height") val fileHeight: Int? = null,
  @SerialName("file_size") val fileSize: Long? = null,
  @SerialName("video_duration") val videoDuration: Double? = null,
  @SerialName("is_favorited") val isFavorited: Boolean = false,
  @SerialName("fav_count") val favCount: Int = 0,
  @SerialName("vote_count") val voteCount: Int = 0,
  @SerialName("total_score") val totalScore: Int = 0,
  @SerialName("user_vote") val userVote: Int? = null,
  @SerialName("parent_id") @Serializable(with = LenientStringSerializer::class) val parentId: String? = null,
  @SerialName("has_children") val hasChildren: Boolean = false,
  @SerialName("redirect_to_signup") val redirectToSignup: Boolean = false,
  val tags: List<TagDto> = emptyList(),
  @SerialName("tag_names") val tagNames: List<String> = emptyList(),
  @SerialName("created_at") @Serializable(with = SankakuInstantSerializer::class) val createdAt: Instant? = null,
  val sequence: Int? = null,
  @SerialName("in_visible_pool") val inVisiblePool: Boolean = false,
) {
  @Serializable
  data class Author(val name: String, val avatar: String? = null)
}

@Serializable
data class TagDto(
  val name: String,
  val type: Int = 0,
  @SerialName("tagName") val tagName: String? = null,
  @SerialName("post_count") val postCount: Int? = null,
  @Serializable(with = LenientStringSerializer::class) val id: String? = null,
  @SerialName("is_following") val isFollowing: Boolean = false,
  @SerialName("pool_count") val poolCount: Int? = null,
) {
  val category: TagType get() = TagType.fromId(type)

  val searchName: String get() = tagName ?: name

  val displayName: String get() = TagNames.shown(name, tagName)
}

@Serializable
data class TagAndWiki(val tag: TagDto, val wiki: WikiDto? = null)

@Serializable
data class FollowedTags(val list: List<TagDto> = emptyList())

@Serializable
data class WikiDto(val title: String? = null, val body: String? = null)

@Serializable
data class TagSuggestion(
  val tagName: String,
  val name: String = tagName,
  @SerialName("name_en") val nameEn: String? = null,
  val type: Int = 0,
  val count: Int? = null,
  @SerialName("post_count") val postCount: Int? = null,
  val rating: String? = null,
) {
  val category: TagType get() = TagType.fromId(type)
  val posts: Int? get() = postCount ?: count

  fun over(older: TagSuggestion?): TagSuggestion = if (older == null) this else copy(
    nameEn = nameEn ?: older.nameEn,
    count = count ?: older.count,
    postCount = postCount ?: older.postCount,
    rating = rating ?: older.rating,
  )

  val displayName: String get() = TagNames.shown(nameEn ?: name, tagName).removePrefix("-")

  internal fun excluded() = if (tagName.startsWith("-")) this else copy(tagName = "-$tagName")
}

@Serializable
data class Page<T>(val data: List<T>, val meta: Meta)

@Serializable
data class Meta(val next: String? = null)

@Serializable
data class UserSummary(
  @Serializable(with = LenientStringSerializer::class) val id: String,
  val name: String,
  @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
internal data class UserResponse(val user: UserSummary)

@Serializable
internal data class TokenResponse(
  val success: Boolean? = null,
  @SerialName("access_token") val accessToken: String? = null,
  @SerialName("refresh_token") val refreshToken: String? = null,
  @SerialName("access_token_ttl") val accessTokenTtl: Long? = null,
  @SerialName("refresh_token_ttl") val refreshTokenTtl: Long? = null,
  @SerialName("current_user") val currentUser: UserSummary? = null,
)

@Serializable
internal data class VoteResponse(
  val success: Boolean? = null,
  @SerialName("vote_count") val voteCount: Int? = null,
)

@Serializable
internal data class FavouriteResponse(
  val success: Boolean? = null,
  @SerialName("post_id") @Serializable(with = LenientStringSerializer::class) val postId: String? = null,
)

@Serializable
internal data class BlacklistCount(@SerialName("used_rules") val usedRules: Int? = null)

@Serializable
internal data class BlacklistResponse(val count: BlacklistCount? = null)

@Serializable
internal data class BlacklistRule(@Serializable(with = LenientStringSerializer::class) val id: String? = null)

@Serializable
internal data class BlacklistAddResponse(
  @SerialName("new_blacklist") val rule: BlacklistRule? = null,
  val count: BlacklistCount? = null,
)

@Serializable
internal data class SuccessResponse(val success: Boolean = false)

object SankakuInstantSerializer : KSerializer<Instant> {
  @Serializable
  private data class Surrogate(val s: Long, val n: Long = 0)

  override val descriptor: SerialDescriptor = Surrogate.serializer().descriptor

  override fun deserialize(decoder: Decoder): Instant {
    val raw = decoder.decodeSerializableValue(Surrogate.serializer())
    return Instant.fromEpochSeconds(raw.s, raw.n)
  }

  override fun serialize(encoder: Encoder, value: Instant) {
    encoder.encodeSerializableValue(
      Surrogate.serializer(),
      Surrogate(value.epochSeconds, value.nanosecondsOfSecond.toLong()),
    )
  }
}

object LenientStringSerializer : KSerializer<String> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): String {
    val element = (decoder as JsonDecoder).decodeJsonElement()
    return (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull ?: element.toString()
  }

  override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}
