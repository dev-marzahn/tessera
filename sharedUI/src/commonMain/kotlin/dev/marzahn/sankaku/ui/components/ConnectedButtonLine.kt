package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class LineButtonStyle { Tonal, Emphasized, Filled, Inverse }

class LineButton(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val style: LineButtonStyle,
  val onClick: () -> Unit,
  val name: String = label,
  val announcedState: String? = null,
  val focusOnFirstShow: FocusOnFirstShow? = null,
)

@Composable
fun ConnectedButtonLine(buttons: List<LineButton>, modifier: Modifier = Modifier) {
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val direction = LocalLayoutDirection.current
  val typography = MaterialTheme.typography
  val labels = buttons.map { it.label }
  BoxWithConstraints(modifier) {
    val layout = remember(labels, maxWidth, density, direction, typography) {
      lineLayout(labels, maxWidth, measurer, density, direction, typography)
    }
    LineButtons(buttons, layout)
  }
}

internal class ButtonSpec(
  val gap: Dp,
  val padding: PaddingValues,
  val minHeight: Dp,
  val iconSize: Dp,
  val iconGap: Dp,
  val iconBesideLabel: Boolean,
  val labelStyle: (Typography) -> TextStyle,
) {
  fun labelWidth(width: Dp, perRow: Int, direction: LayoutDirection): Dp {
    val button = (width - gap * (perRow - 1)) / perRow
    val inside = button - padding.calculateStartPadding(direction) - padding.calculateEndPadding(direction)
    return if (iconBesideLabel) inside - iconSize - iconGap else inside
  }
}

internal sealed interface LineLayout {
  val spec: ButtonSpec

  fun buttonsPerRow(count: Int): Int

  data object Connected : LineLayout {
    override val spec get() = CONNECTED_SPEC

    override fun buttonsPerRow(count: Int) = count
  }

  data class Rows(val perRow: Int) : LineLayout {
    override val spec get() = ROW_SPEC

    override fun buttonsPerRow(count: Int) = perRow
  }
}

internal fun lineLayout(
  labels: List<String>,
  width: Dp,
  measurer: TextMeasurer,
  density: Density,
  direction: LayoutDirection,
  typography: Typography,
): LineLayout {
  fun fits(layout: LineLayout): Boolean {
    val style = layout.spec.labelStyle(typography)
    val slot = with(density) {
      layout.spec.labelWidth(width, layout.buttonsPerRow(labels.size), direction).roundToPx().coerceAtLeast(0)
    }
    fun measure(text: String, constraints: Constraints = Constraints()) =
      measurer.measure(text, style, constraints = constraints, layoutDirection = direction, density = density)
    return labels.all { label ->
      measure(label, Constraints(maxWidth = slot)).lineCount <= MAX_LABEL_LINES &&
        label.split(' ').all { word -> measure(word).size.width <= slot }
    }
  }
  if (density.fontScale > LARGE_FONT_SCALE) return LineLayout.Rows(1)
  return listOf(LineLayout.Connected, LineLayout.Rows(2)).firstOrNull(::fits) ?: LineLayout.Rows(1)
}

@Composable
private fun LineButtons(buttons: List<LineButton>, layout: LineLayout) {
  val spec = layout.spec
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spec.gap)) {
    buttons.chunked(layout.buttonsPerRow(buttons.size)).forEach { row ->
      val rowModifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
      val content: @Composable RowScope.() -> Unit = {
        row.forEachIndexed { index, button ->
          key(button.id) {
            StyledButton(button, spec, layout.shapes(index, row.lastIndex), Modifier.weight(1f).fillMaxHeight())
          }
        }
      }
      if (layout == LineLayout.Connected) {
        ConnectedGroup(rowModifier, content)
      } else {
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(spec.gap), content = content)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StyledButton(button: LineButton, spec: ButtonSpec, shapes: ButtonShapes, modifier: Modifier) {
  val focus = button.focusOnFirstShow?.let { rememberFocusOnFirstShow(it) }
  Button(
    onClick = button.onClick,
    shapes = shapes,
    colors = button.style.colors(),
    contentPadding = spec.padding,
    modifier = modifier
      .heightIn(min = spec.minHeight)
      .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
      .semantics {
        contentDescription = button.name
        button.announcedState?.let {
          stateDescription = it
          liveRegion = LiveRegionMode.Polite
        }
      },
  ) {
    val icon = @Composable {
      Icon(button.icon, contentDescription = null, tint = button.iconTint(), modifier = Modifier.size(spec.iconSize))
    }
    val label = @Composable {
      Text(
        button.label,
        style = spec.labelStyle(MaterialTheme.typography),
        textAlign = TextAlign.Center,
        modifier = Modifier.clearAndSetSemantics {},
      )
    }
    if (spec.iconBesideLabel) {
      icon()
      Spacer(Modifier.size(spec.iconGap))
      label()
    } else {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spec.iconGap)) {
        icon()
        label()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LineLayout.shapes(index: Int, last: Int): ButtonShapes = when {
  this != LineLayout.Connected || last == 0 -> ButtonDefaults.shapes()
  index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes().asButtonShapes()
  index == last -> ButtonGroupDefaults.connectedTrailingButtonShapes().asButtonShapes()
  else -> ButtonGroupDefaults.connectedMiddleButtonShapes().asButtonShapes()
}

private fun ToggleButtonShapes.asButtonShapes() = ButtonShapes(shape, pressedShape)

@Composable
private fun LineButtonStyle.colors(): ButtonColors {
  val scheme = MaterialTheme.colorScheme
  return when (this) {
    LineButtonStyle.Tonal -> ButtonDefaults.filledTonalButtonColors()
    LineButtonStyle.Emphasized -> ButtonDefaults.buttonColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer)
    LineButtonStyle.Filled -> ButtonDefaults.buttonColors()
    LineButtonStyle.Inverse -> ButtonDefaults.buttonColors(containerColor = scheme.inverseSurface, contentColor = scheme.inverseOnSurface)
  }
}

@Composable
private fun LineButton.iconTint(): Color =
  if (style == LineButtonStyle.Inverse) MaterialTheme.colorScheme.inversePrimary else LocalContentColor.current

private const val MAX_LABEL_LINES = 2

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val CONNECTED_SPEC = ButtonSpec(
  gap = ButtonGroupDefaults.ConnectedSpaceBetween,
  padding = PaddingValues(horizontal = 12.dp, vertical = 1.dp),
  minHeight = 56.dp,
  iconSize = 18.dp,
  iconGap = 4.dp,
  iconBesideLabel = false,
  labelStyle = Typography::labelMedium,
)

private val ROW_SPEC = ButtonSpec(
  gap = 8.dp,
  padding = ButtonDefaults.ButtonWithIconContentPadding,
  minHeight = 48.dp,
  iconSize = ButtonDefaults.IconSize,
  iconGap = ButtonDefaults.IconSpacing,
  iconBesideLabel = true,
  labelStyle = Typography::labelLarge,
)
