package dev.marzahn.sankaku.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PanelToggleButton(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  checkedContainer: Color,
  uncheckedContainer: Color,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val container by animateColorAsState(if (checked) checkedContainer else uncheckedContainer, effectsSpec())
  val contentColor = MaterialTheme.colorScheme.onSurface
  IconToggleButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    shapes = IconButtonDefaults.toggleableShapes(),
    colors = IconButtonDefaults.iconToggleButtonColors(
      containerColor = container,
      contentColor = contentColor,
      checkedContainerColor = container,
      checkedContentColor = contentColor,
    ),
    modifier = modifier,
    content = content,
  )
}
