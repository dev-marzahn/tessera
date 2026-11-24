package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.error

data class AppSnackbarVisuals(
  override val message: String,
  override val actionLabel: String? = null,
  override val duration: SnackbarDuration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
  val isError: Boolean = false,
  override val withDismissAction: Boolean = false,
) : SnackbarVisuals

@Composable
fun AppSnackbarHost(state: SnackbarHostState, swipeable: Boolean, modifier: Modifier = Modifier, errorIcons: Boolean = swipeable) {
  SnackbarHost(state, modifier) { data ->
    if (swipeable) {
      key(data) {
        SwipeToDismissBox(
          state = rememberSwipeToDismissBoxState(),
          backgroundContent = {},
          onDismiss = { data.dismiss() },
        ) {
          AppSnackbar(data, errorIcons)
        }
      }
    } else {
      AppSnackbar(data, errorIcons)
    }
  }
}

@Composable
private fun AppSnackbar(data: SnackbarData, errorIcons: Boolean) {
  val visuals = data.visuals
  if (!errorIcons || visuals !is AppSnackbarVisuals || !visuals.isError) {
    Snackbar(data)
    return
  }
  val action = visuals.actionLabel
  Snackbar(
    modifier = Modifier.padding(12.dp),
    action = action?.let {
      {
        TextButton(
          onClick = data::performAction,
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary),
        ) { Text(it) }
      }
    },
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      val onDark = MaterialTheme.colorScheme.inverseSurface.luminance() < 0.5f
      Icon(error, contentDescription = null, tint = if (onDark) ErrorOnDark else ErrorOnLight, modifier = Modifier.size(20.dp))
      Text(visuals.message)
    }
  }
}

private val ErrorOnDark = Color(0xFFFFB4AB)
private val ErrorOnLight = Color(0xFFBA1A1A)
