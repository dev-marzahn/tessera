package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.visibility_off

const val SEEN_IMAGE_ALPHA = 0.85f

const val SEEN_BADGE_SCRIM_ALPHA = 0.6f

fun Modifier.seenImage(seen: Boolean): Modifier = if (seen) alpha(SEEN_IMAGE_ALPHA) else this

@Composable
fun SeenBadge(modifier: Modifier = Modifier) {
  Surface(
    shape = CircleShape,
    color = Color.Black.copy(alpha = SEEN_BADGE_SCRIM_ALPHA),
    contentColor = Color.White,
    modifier = modifier.semantics { contentDescription = "Seen" },
  ) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      Icon(visibility_off, contentDescription = null, modifier = Modifier.size(16.dp))
    }
  }
}
