package dev.marzahn.sankaku.features.post

import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.PostTitle
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.components.KEPT_AGAIN
import dev.marzahn.sankaku.ui.components.alreadySpoken
import dev.marzahn.sankaku.ui.components.inTabLabel
import kotlin.math.roundToInt

internal fun spokenTitle(post: PostDto): String = PostTitle.of(post) ?: PostTitle.UNTITLED

internal fun postKind(post: PostDto): String = when {
  isVideo(post) -> "video"
  post.fileType == "image/gif" -> "animated GIF"
  isFlash(post) -> "Flash animation"
  else -> "image"
}

internal fun pictureDescription(post: PostDto): String = "${spokenTitle(post)}, ${postKind(post)}"

internal fun cellDescription(
  post: PostDto,
  openInTab: Boolean,
  unfavourited: Boolean = false,
  alreadyTab: Int? = null,
  seen: Boolean = false,
  keptAgain: Boolean = false,
  touch: Boolean = platformIsTouch,
  playsVideo: Boolean = platformPlaysVideo(post),
  new: Boolean = false,
): String =
  listOfNotNull(
    spokenTitle(post),
    if (new) "New" else null,
    when {
      unfavourited -> "Unfavourited"
      post.isFavorited -> "Favourited"
      else -> null
    },
    if (seen) "Seen" else null,
    when {
      !openInTab -> null
      keptAgain -> KEPT_AGAIN
      alreadyTab != null -> alreadySpoken(alreadyTab, touch)
      else -> inTabLabel(touch)
    },
    mediaKind(post, playsVideo)?.let { mediaDescription(post, it) },
  ).joinToString(". ")

internal fun voteState(userVote: Int?): String = "Your vote, ${userVote ?: "none"}"

internal fun ratingDescription(post: PostDto): String {
  val average = averageVote(post) ?: return "No votes"
  return "Average $average from ${post.voteCount} ${if (post.voteCount == 1) "vote" else "votes"}"
}

internal fun SemanticsPropertyReceiver.voteSemantics(post: PostDto, onVote: (Int?) -> Unit, onSignIn: (() -> Unit)?) {
  contentDescription = ratingDescription(post)
  stateDescription = voteState(post.userVote)
  if (onSignIn != null) {
    onClick("Sign in to vote") {
      onSignIn()
      true
    }
    return
  }
  progressBarRangeInfo = ProgressBarRangeInfo((post.userVote ?: 0).toFloat(), 0f..5f, steps = 4)
  setProgress("Vote") { target ->
    val vote = target.roundToInt().coerceIn(0, 5).takeIf { it > 0 }
    if (vote != post.userVote) onVote(vote)
    true
  }
  customActions = (1..5).filter { it != post.userVote }.map { n ->
    CustomAccessibilityAction("Vote $n") {
      onVote(n)
      true
    }
  } + listOfNotNull(
    post.userVote?.let {
      CustomAccessibilityAction("Take vote back") {
        onVote(null)
        true
      }
    },
  )
}

internal fun favouriteDescription(count: String, key: String?): String =
  "Favourite, $count" + (key?.let { " ($it)" } ?: "")
