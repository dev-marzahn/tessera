package dev.marzahn.sankaku.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.currentCoroutineContext
import com.google.fonts.icons.favorite_filled
import com.google.fonts.icons.heart_broken

@Composable
fun FavouriteBurst(favourited: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier, size: Dp = BurstSize) {
  val finished by rememberUpdatedState(onFinished)
  val pop = fastSpatialSpec<Float>()
  val fade = effectsSpec<Float>(slow = true)
  val scale = remember { Animatable(0f) }
  val alpha = remember { Animatable(1f) }
  LaunchedEffect(Unit) {
    scale.animateTo(1f, pop)
    alpha.animateTo(0f, fade)
    finished()
  }
  val icon = if (favourited) favorite_filled else heart_broken
  Box(
    modifier
      .size(size)
      .testTag(FAVOURITE_BURST_TAG)
      .semantics {
        hideFromAccessibility()
        this[BurstFavourited] = favourited
      }
      .graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
      },
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.45f), modifier = Modifier.size(size).blur(6.dp))
    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size))
  }
}

@Composable
fun FavouriteBursts(hearts: Flow<Boolean>, modifier: Modifier = Modifier, size: Dp = BurstSize) {
  var burst by remember { mutableStateOf<Burst?>(null) }
  LaunchedEffect(hearts) {
    hearts.collect { favourited ->
      if ((currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) return@collect
      burst = Burst(favourited, (burst?.id ?: 0) + 1)
    }
  }
  burst?.let {
    key(it.id) { FavouriteBurst(it.favourited, onFinished = { burst = null }, modifier, size) }
  }
}

private data class Burst(val favourited: Boolean, val id: Int)

const val FAVOURITE_BURST_TAG = "FavouriteBurst"

val BurstFavourited = SemanticsPropertyKey<Boolean>("BurstFavourited")

private val BurstSize = 56.dp

val PictureBurstSize = 96.dp
