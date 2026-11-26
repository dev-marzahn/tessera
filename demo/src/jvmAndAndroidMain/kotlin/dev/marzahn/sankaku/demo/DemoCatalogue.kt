package dev.marzahn.sankaku.demo

import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.api.dto.TagType
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal data class DemoPost(
  val media: String,
  val fileType: String,
  val extension: String,
  val width: Int,
  val height: Int,
  val tags: List<TagDto>,
  val postedAt: Instant,
  val videoSeconds: Double?,
) {
  val id: String get() = "demo-$media"

  val isVideo: Boolean get() = fileType.startsWith("video/")

  val fileName: String get() = "$media.$extension"

  val previewName: String get() = "$media-preview.jpg"

  val tagNames: List<String> get() = tags.map { it.searchName }

  fun fakeMd5(): String = id.hashCode().toUInt().toString(16).padStart(8, '0').repeat(4)
}

internal object DemoCatalogue {
  const val RATING = "s"

  private val NEWEST = Instant.parse("2026-01-12T12:00:00Z")

  private class Spec(
    val media: String,
    val fileType: String,
    val extension: String,
    val width: Int,
    val height: Int,
    val tags: List<String>,
    val videoSeconds: Double? = null,
  )

  private val TYPES = mapOf(
    "tessera_demo" to TagType.Copyright,
    "test_pattern" to TagType.Artist,
    "portrait" to TagType.Medium,
    "landscape" to TagType.Medium,
    "still_image" to TagType.Medium,
    "video" to TagType.Medium,
    "animated" to TagType.Medium,
    "filler" to TagType.General,
  )

  private val MANY_TAGS = listOf(
    "sky", "cloud", "sun", "moon", "star", "tree", "leaf", "flower", "river", "lake", "mountain", "hill", "road", "bridge",
    "house", "window", "door", "lamp", "chair", "table", "book", "cup", "clock", "bicycle", "train", "boat", "kite", "balloon",
    "umbrella", "hat", "scarf", "glove", "shoe", "bag", "map", "key", "letter", "bell", "candle", "ladder",
  )

  private val SPECS = listOf(
    Spec("portrait-image", "image/jpeg", "jpg", 720, 1280, listOf("portrait", "still_image")),
    Spec("landscape-image", "image/jpeg", "jpg", 1280, 720, listOf("landscape", "still_image")),
    Spec("animated-gif", "image/gif", "gif", 360, 360, listOf("animated")),
    Spec("portrait-video", "video/mp4", "mp4", 360, 640, listOf("portrait", "video"), videoSeconds = 6.0),
    Spec("landscape-video", "video/mp4", "mp4", 640, 360, listOf("landscape", "video"), videoSeconds = 6.0),
    Spec("many-tags", "image/jpeg", "jpg", 900, 900, listOf("still_image") + MANY_TAGS),
    Spec("filler-1", "image/jpeg", "jpg", 600, 800, listOf("filler", "portrait")),
    Spec("filler-2", "image/jpeg", "jpg", 800, 600, listOf("filler", "landscape")),
    Spec("filler-3", "image/jpeg", "jpg", 700, 700, listOf("filler")),
    Spec("filler-4", "image/jpeg", "jpg", 600, 800, listOf("filler", "portrait")),
    Spec("filler-5", "image/jpeg", "jpg", 800, 600, listOf("filler", "landscape")),
    Spec("filler-6", "image/jpeg", "jpg", 700, 700, listOf("filler")),
  )

  private fun Spec.tagNames() = listOf(media.replace('-', '_'), "tessera_demo", "test_pattern") + tags

  private val COUNTS: Map<String, Int> = SPECS.flatMap { it.tagNames() }.groupingBy { it }.eachCount()

  val tags: List<TagDto> = COUNTS.keys.map(::tag)

  val posts: List<DemoPost> = SPECS.mapIndexed { age, spec ->
    DemoPost(
      media = spec.media,
      fileType = spec.fileType,
      extension = spec.extension,
      width = spec.width,
      height = spec.height,
      tags = spec.tagNames().map { name -> tags.first { it.searchName == name } },
      postedAt = NEWEST - age.days,
      videoSeconds = spec.videoSeconds,
    )
  }

  private fun tag(name: String): TagDto {
    val type = TYPES[name] ?: if (name in MANY_TAGS) TagType.General else TagType.Character
    return TagDto(name = name.replace('_', ' '), type = type.id, tagName = name, id = "demo-tag-$name", postCount = COUNTS[name])
  }
}
