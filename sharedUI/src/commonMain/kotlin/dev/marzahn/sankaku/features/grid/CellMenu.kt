package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.google.fonts.icons.favorite
import com.google.fonts.icons.heart_broken
import com.google.fonts.icons.image
import com.google.fonts.icons.tab
import dev.marzahn.sankaku.ui.components.GroupedMenu
import dev.marzahn.sankaku.ui.components.KbdBadge
import dev.marzahn.sankaku.ui.components.MenuAction
import dev.marzahn.sankaku.ui.components.MenuGroup
import dev.marzahn.sankaku.ui.components.Shortcuts

@Composable
internal fun CellMenu(
  favourited: Boolean,
  onDismiss: () -> Unit,
  onOpen: () -> Unit,
  onOpenInBackground: () -> Unit,
  onToggleFavourite: () -> Unit,
) {
  GroupedMenu(expanded = true, onDismissRequest = onDismiss) {
    MenuGroup(0, 2) {
      MenuAction(
        text = { Text("Open") },
        leadingIcon = { Icon(image, contentDescription = null) },
        trailingIcon = { KbdBadge(Shortcuts.OpenHere.label) },
        onClick = {
          onDismiss()
          onOpen()
        },
      )
      MenuAction(
        text = {
          Column {
            Text(openInBackgroundLabel(touch = false))
            Text(MIDDLE_CLICK, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        leadingIcon = { Icon(tab, contentDescription = null) },
        trailingIcon = { KbdBadge(Shortcuts.OpenInTab.label) },
        onClick = {
          onDismiss()
          onOpenInBackground()
        },
      )
    }
    MenuGroup(1, 2) {
      MenuAction(
        text = { Text(if (favourited) "Unfavourite" else "Favourite") },
        leadingIcon = { Icon(if (favourited) heart_broken else favorite, contentDescription = null) },
        onClick = {
          onDismiss()
          onToggleFavourite()
        },
      )
    }
  }
}

internal const val MIDDLE_CLICK = "Middle-click"
