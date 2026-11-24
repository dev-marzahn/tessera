package dev.marzahn.sankaku.shell

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.coroutineContext
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import dev.marzahn.sankaku.features.grid.imageKey
import dev.marzahn.sankaku.features.grid.sharingConfig
import dev.marzahn.sankaku.ui.components.spatialBoundsTransform

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalShellTransitions = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalUnderQueuePage = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

val LocalQueuePageOpening = compositionLocalOf { false }

val LocalQueuePageVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun underQueuePageMoving(): Boolean {
  val under = LocalUnderQueuePage.current ?: return false
  val transition = under.transition
  val settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
  return !settled || LocalQueuePageOpening.current
}

@Composable
fun Modifier.sharedPicture(
  id: String,
  visibility: AnimatedVisibilityScope?,
  enabled: Boolean,
  slow: Boolean,
  clip: Shape,
): Modifier = sharedImage(imageKey(id), visibility, enabled, slow, clip, remeasure = false)

@Composable
fun Modifier.sharedMosaicThumb(md5: String, visibility: AnimatedVisibilityScope?, enabled: Boolean, clip: Shape, cell: Boolean): Modifier =
  sharedImage(mosaicKey(md5), visibility, enabled, slow = false, clip, remeasure = cell)

fun mosaicKey(md5: String) = "mosaic-$md5"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.sharedImage(
  key: Any,
  visibility: AnimatedVisibilityScope?,
  enabled: Boolean,
  slow: Boolean,
  clip: Shape,
  remeasure: Boolean,
): Modifier {
  val shell = LocalShellTransitions.current
  val bounds = spatialBoundsTransform(slow)
  if (shell == null || visibility == null) return this
  return with(shell) {
    this@sharedImage.sharedBounds(
      rememberSharedContentState(key, sharingConfig(enabled)),
      visibility,
      enter = EnterTransition.None,
      exit = ExitTransition.None,
      boundsTransform = bounds,
      resizeMode = if (remeasure) {
        SharedTransitionScope.ResizeMode.RemeasureToBounds
      } else {
        SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop)
      },
      clipInOverlayDuringTransition = OverlayClip(clip),
    )
  }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.aboveSharedTransitions(enabled: Boolean = true): Modifier {
  val shell = LocalShellTransitions.current ?: return this
  return with(shell) {
    this@aboveSharedTransitions.renderInSharedTransitionScopeOverlay(ABOVE_SCREENS_Z) { enabled && isTransitionActive }
  }
}

private const val ABOVE_SCREENS_Z = 10f

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitions(modifier: Modifier = Modifier, content: @Composable SharedTransitionScope.() -> Unit) {
  val shell = LocalShellTransitions.current
  if (shell == null) {
    SharedTransitionLayout(modifier, content)
  } else {
    Box(modifier) { shell.content() }
  }
}

internal suspend fun animationsOff(): Boolean = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f

internal const val MATCH_FRAMES = 2
