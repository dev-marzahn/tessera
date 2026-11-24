package dev.marzahn.sankaku.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.fonts.icons.tab
import dev.marzahn.sankaku.platformIsTouch
import kotlinx.coroutines.delay


data class AlreadyTab(val postId: String, val position: Int, val serial: Int, val again: Boolean = false)

@Stable
class AlreadyInTab {
  var shown by mutableStateOf<AlreadyTab?>(null)
    internal set

  fun show(postId: String, position: Int, again: Boolean = false) {
    shown = AlreadyTab(postId, position, (shown?.serial ?: 0) + 1, again)
  }

  fun of(postId: String): AlreadyTab? = shown?.takeIf { it.postId == postId }
}

@Composable
fun rememberAlreadyInTab(): AlreadyInTab {
  val state = remember { AlreadyInTab() }
  val shown = state.shown
  LaunchedEffect(shown) {
    if (shown == null) return@LaunchedEffect
    delay(ALREADY_TAB_MS)
    state.shown = null
  }
  return state
}

const val ALREADY_TAB_MS = 1500L

internal fun inTabLabel(touch: Boolean = platformIsTouch): String = if (touch) "Kept" else "In tab"

internal fun alreadyLabel(position: Int, touch: Boolean = platformIsTouch): String =
  if (touch) "Already item $position" else "Already tab $position"

internal fun alreadySpoken(position: Int, touch: Boolean = platformIsTouch): String =
  if (touch) "Already item $position" else "Already in tab $position"

@Composable
fun InTabChip(
  modifier: Modifier = Modifier,
  already: AlreadyTab? = null,
  touch: Boolean = platformIsTouch,
  elevated: Boolean = true,
) {
  val color by animateColorAsState(
    if (already != null) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.secondaryContainer,
    effectsSpec(),
  )
  val contentColor by animateColorAsState(
    if (already != null) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSecondaryContainer,
    effectsSpec(),
  )
  val pulse = rememberPulse(already?.serial, PULSE_SCALE)
  BoxWithConstraints(
    modifier.clearAndSetSemantics {
      if (already != null) {
        liveRegion = LiveRegionMode.Polite
        contentDescription = if (already.again) KEPT_AGAIN else alreadySpoken(already.position, touch)
      } else {
        contentDescription = inTabLabel(touch)
      }
    },
  ) {
    val showIcon = inTabChipShowsIcon(maxWidth, already != null)
    val compact = !showIcon && already == null
    Surface(
      shape = RoundedCornerShape(50),
      color = color,
      contentColor = contentColor,
      shadowElevation = if (elevated) 2.dp else 0.dp,
      modifier = Modifier
        .graphicsLayer {
          scaleX = pulse.value
          scaleY = pulse.value
          transformOrigin = TransformOrigin(0f, 1f)
        },
    ) {
      Row(
        Modifier.animateContentSize(spatialSpec()).height(24.dp)
          .padding(start = if (compact) 6.dp else 7.dp, end = if (compact) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        if (showIcon) Icon(tab, contentDescription = null, modifier = Modifier.size(14.dp))
        val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        Text(
          when {
            already == null -> inTabLabel(touch)
            already.again -> KEPT_AGAIN
            else -> alreadyLabel(already.position, touch)
          },
          style = style,
          maxLines = 1,
          autoSize = TextAutoSize.StepBased(minFontSize = MIN_FONT, maxFontSize = style.fontSize),
        )
      }
    }
  }
}

const val KEPT_AGAIN = "Kept again"

private const val PULSE_SCALE = 1.12f

internal fun inTabChipShowsIcon(maxWidth: Dp, already: Boolean): Boolean =
  maxWidth >= if (already) ALREADY_ROOMY else PLAIN_ROOMY

private val ALREADY_ROOMY = 120.dp

private val PLAIN_ROOMY = 70.dp
private val MIN_FONT = 9.sp
