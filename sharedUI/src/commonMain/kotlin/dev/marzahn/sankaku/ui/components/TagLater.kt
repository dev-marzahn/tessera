package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.more_time
import com.google.fonts.icons.schedule_filled
import com.google.fonts.icons.view_agenda

enum class TagLaterState {
  Open,

  Opened,

  InQueue,
}

class TagLater(val state: TagLaterState, val description: String, val onClick: () -> Unit)

fun openForLaterLabel(name: String): String = "Open $name for later"

fun goToQueuedLabel(name: String, position: Int): String = "Go to $name, item $position"

internal val TagLaterState.icon: ImageVector
  get() = when (this) {
    TagLaterState.Open -> more_time
    TagLaterState.Opened -> schedule_filled
    TagLaterState.InQueue -> view_agenda
  }

const val LATER = "Later"

@Composable
fun LaterButton(onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
  TextButton(onClick = onClick, modifier = modifier.semantics { contentDescription = description }) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Icon(more_time, contentDescription = null, modifier = Modifier.size(18.dp))
      Text(LATER, modifier = Modifier.padding(end = 2.dp))
    }
  }
}
