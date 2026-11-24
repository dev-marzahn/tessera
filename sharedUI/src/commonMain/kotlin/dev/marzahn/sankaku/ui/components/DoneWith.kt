package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.close

@Composable
fun DoneWith(
  name: String,
  keptStay: Int,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  closeLabel: String = "Close $name",
  onLater: (() -> Unit)? = null,
  followAndClose: DoneWithFollow? = null,
) {
  Column(
    modifier.fillMaxWidth().padding(vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Done with $name?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      TonalButton(closeLabel, close, onClose, Modifier.semantics { heading() })
      if (followAndClose != null) {
        OutlineIconButton(
          FOLLOW_AND_CLOSE,
          followAndClose.icon,
          followAndClose.onClick,
          Modifier.semantics { contentDescription = followAndCloseName(name) },
        )
      }
    }
    if (followAndClose != null) {
      Text(
        followAndCloseNote(name),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
    if (onLater != null) TextActionButton(DONE_WITH_LATER, onClick = onLater)
    if (keptStay > 0) {
      Text(
        "$keptStay kept stay",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

class DoneWithFollow(val icon: ImageVector, val onClick: () -> Unit)

const val DONE_WITH_LATER = "Later"

const val CLOSE_NEXT_CARD = "Close · next card"

const val CLOSE_TO_KEPT = "Close"

const val FOLLOW_AND_CLOSE = "Follow and close"

fun followAndCloseName(name: String) = "Follow $name and close"

fun followAndCloseNote(name: String) = "New posts by $name will come in Following"
