package dev.marzahn.sankaku.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  Row(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedLeadingPane(
  modifier: Modifier = Modifier,
  checked: Boolean = false,
  rejected: Int = 0,
  mirrored: Boolean = false,
  content: @Composable RowScope.() -> Unit,
) {
  val colors = connectedToggleColors()
  val container by animateColorAsState(if (checked) colors.checkedContainerColor else colors.containerColor, effectsSpec())
  val morph by animateFloatAsState(if (checked) 1f else 0f, fastSpatialSpec())
  val outer = if (mirrored) ButtonGroupDefaults.connectedTrailingButtonShape else ButtonGroupDefaults.connectedLeadingButtonShape
  val shape = morphedShape(outer, ButtonGroupDefaults.connectedButtonCheckedShape, morph)
  Surface(
    shape = shape,
    color = container,
    contentColor = if (checked) colors.checkedContentColor else colors.contentColor,
    modifier = modifier.rejected(rejected, shape).heightIn(min = ConnectedHeight),
  ) {
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedTrailingToggle(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  rejected: Int = 0,
  mirrored: Boolean = false,
  iconOnly: Boolean = false,
  content: @Composable RowScope.() -> Unit,
) {
  val shapes = if (mirrored) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes()
  ToggleButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    shapes = shapes,
    colors = connectedToggleColors(),
    contentPadding = if (iconOnly) PaddingValues(horizontal = 14.dp) else PaddingValues(start = 12.dp, end = 16.dp),
    modifier = modifier.rejected(rejected, if (checked) shapes.checkedShape else shapes.shape).heightIn(min = ConnectedHeight),
    content = content,
  )
}

private fun morphedShape(from: Shape, to: CornerBasedShape, fraction: Float): Shape {
  if (from !is CornerBasedShape) return from
  return object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
      fun corner(a: CornerSize, b: CornerSize) = CornerSize(lerp(a.toPx(size, density), b.toPx(size, density), fraction))
      return RoundedCornerShape(
        topStart = corner(from.topStart, to.topStart),
        topEnd = corner(from.topEnd, to.topEnd),
        bottomEnd = corner(from.bottomEnd, to.bottomEnd),
        bottomStart = corner(from.bottomStart, to.bottomStart),
      ).createOutline(size, layoutDirection, density)
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun connectedToggleColors() = ToggleButtonDefaults.tonalToggleButtonColors(
  containerColor = MaterialTheme.colorScheme.surfaceContainer,
  contentColor = MaterialTheme.colorScheme.onSurface,
  checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
  checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

private val ConnectedHeight = 48.dp
