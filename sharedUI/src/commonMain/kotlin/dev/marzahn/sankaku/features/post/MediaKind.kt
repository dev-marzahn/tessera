package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.fonts.icons.play_arrow_filled
import com.google.fonts.icons.play_disabled_filled
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.ui.components.MediaBadge

enum class MediaKind {
  Video,

  CantPlayVideo,

  Gif,

  Flash,
}

fun mediaKind(post: PostDto, playsVideo: Boolean = platformPlaysVideo(post)): MediaKind? = when {
  isVideo(post) -> if (playsVideo) MediaKind.Video else MediaKind.CantPlayVideo
  isFlash(post) -> MediaKind.Flash
  post.fileType == "image/gif" -> MediaKind.Gif
  else -> null
}

internal fun mediaDescription(post: PostDto, kind: MediaKind): String = when (kind) {
  MediaKind.Video -> listOfNotNull("Video", videoBadgeDuration(post)).joinToString(", ")
  MediaKind.CantPlayVideo -> listOfNotNull("Video", videoBadgeDuration(post), "can't play here").joinToString(", ")
  MediaKind.Gif -> "Animated GIF"
  MediaKind.Flash -> "Flash, can't play"
}

@Composable
fun PostMediaBadge(post: PostDto, modifier: Modifier = Modifier) {
  val kind = mediaKind(post) ?: return
  val description = mediaDescription(post, kind)
  when (kind) {
    MediaKind.Video -> MediaBadge(play_arrow_filled, videoBadgeDuration(post), description, modifier)
    MediaKind.CantPlayVideo -> MediaBadge(play_disabled_filled, videoBadgeDuration(post), description, modifier)
    MediaKind.Gif -> MediaBadge(null, "GIF", description, modifier)
    MediaKind.Flash -> MediaBadge(play_disabled_filled, "Flash", description, modifier)
  }
}
