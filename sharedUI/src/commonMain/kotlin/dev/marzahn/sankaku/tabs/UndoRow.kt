package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.undo

@Composable
internal fun UndoRow(
  closedTab: ClosedTab,
  closing: Closing,
  firstShow: (Long) -> Boolean,
  onUndo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val words = closing.words(closedTab)
  val focus = remember { FocusRequester() }
  LaunchedEffect(closedTab.key) { if (firstShow(closedTab.key)) focus.requestFocus() }
  Row(
    modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(colors.inverseSurface)
      .padding(start = 16.dp, end = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      Modifier.weight(1f).padding(vertical = 8.dp).semantics(mergeDescendants = true) {
        contentDescription = words.spoken
        liveRegion = LiveRegionMode.Polite
      },
    ) {
      Text(
        words.shown,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.inverseOnSurface,
        maxLines = rowTextLines(),
        overflow = TextOverflow.Ellipsis,
      )
      if (closedTab.keptStay > 0) {
        Text(
          "${closedTab.keptStay} kept stay",
          style = MaterialTheme.typography.bodySmall,
          color = colors.inverseOnSurface.copy(alpha = 0.8f),
        )
      }
    }
    TextButton(
      onClick = onUndo,
      colors = ButtonDefaults.textButtonColors(contentColor = colors.inversePrimary),
      modifier = Modifier.focusRequester(focus),
    ) {
      Icon(undo, contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(6.dp))
      Text("Undo")
    }
  }
}
