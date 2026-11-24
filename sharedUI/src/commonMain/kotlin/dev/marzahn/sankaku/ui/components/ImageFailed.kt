package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.error
import com.google.fonts.icons.image
import com.google.fonts.icons.refresh

@Composable
fun ImageFailed(noImage: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
  val colors = MaterialTheme.colorScheme
  Surface(color = colors.surfaceContainerHighest, contentColor = colors.onSurfaceVariant, modifier = modifier) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
      val short = maxHeight < SHORT_CELL
      Column(
        Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
      ) {
        if (!short) Icon(if (noImage) image else error, contentDescription = null, modifier = Modifier.size(if (compact) 24.dp else 40.dp))
        if (!short || noImage) Text(
          when {
            noImage -> "No preview"
            compact -> "Couldn't load"
            else -> "Couldn't load the image"
          },
          style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (!noImage) ImageRetryChip(onRetry)
      }
    }
  }
}

private val SHORT_CELL = 120.dp

@Composable
fun VideoFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Surface(
    shape = MaterialTheme.shapes.large,
    color = colors.surfaceContainerHighest.copy(alpha = 0.9f),
    contentColor = colors.onSurfaceVariant,
    modifier = modifier,
  ) {
    Column(
      Modifier.padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(error, contentDescription = null, modifier = Modifier.size(40.dp))
      Text(
        "Couldn't play the video",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
      ImageRetryChip(onRetry, description = "Retry playing the video")
    }
  }
}

@Composable
fun ImageRetryChip(onRetry: () -> Unit, modifier: Modifier = Modifier, description: String = "Retry loading the image") {
  val colors = MaterialTheme.colorScheme
  Surface(
    onClick = onRetry,
    shape = CircleShape,
    color = colors.errorContainer,
    contentColor = colors.onErrorContainer,
    modifier = modifier.heightIn(min = 40.dp).semantics { contentDescription = description },
  ) {
    Row(
      Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(refresh, contentDescription = null, modifier = Modifier.size(18.dp))
      Text("Retry", style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
  }
}
