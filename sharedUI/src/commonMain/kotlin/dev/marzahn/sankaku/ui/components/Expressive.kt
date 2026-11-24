package dev.marzahn.sankaku.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.google.fonts.icons.arrow_upward

@Composable
fun KbdBadge(key: String, modifier: Modifier = Modifier) {
  Surface(
    shape = MaterialTheme.shapes.extraSmall,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
  ) {
    Box(
      Modifier.defaultMinSize(minWidth = KbdSize, minHeight = KbdSize).padding(horizontal = 4.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(key, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp).centredOnLine())
    }
  }
}

private val KbdSize = 16.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Button(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier) { Text(text) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TonalButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
  FilledTonalButton(
    onClick = onClick,
    shapes = ButtonDefaults.shapes(),
    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    modifier = modifier,
  ) { IconAndText(text, icon) }
}

@Composable
private fun IconAndText(text: String, icon: ImageVector) {
  Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
  Spacer(Modifier.size(ButtonDefaults.IconSpacing))
  Text(text)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrimaryIconButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Button(
    onClick = onClick,
    shapes = ButtonDefaults.shapes(),
    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    modifier = modifier,
  ) { IconAndText(text, icon) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OutlineIconButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
  OutlinedButton(
    onClick = onClick,
    shapes = ButtonDefaults.shapes(),
    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    modifier = modifier,
  ) { IconAndText(text, icon) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  TextButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier) { Text(text) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToggleIconButton(checked: Boolean, onCheckedChange: (Boolean) -> Unit, content: @Composable () -> Unit) {
  IconToggleButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    shapes = IconButtonDefaults.toggleableShapes(),
    content = content,
  )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TonalToggleIconButton(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  enabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  FilledTonalIconToggleButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    enabled = enabled,
    shapes = IconButtonDefaults.toggleableShapes(),
    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(containerColor = Color.Transparent),
    content = content,
  )
}

@Composable
fun KeyHint(key: String, label: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
    KbdBadge(key)
    Text(
      label,
      style = MaterialTheme.typography.labelSmall.centredOnLine(),
      color = color,
      modifier = Modifier.offset(y = (-1.5).dp),
    )
  }
}

@Composable
fun LabelWithKey(label: String, key: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = LocalTextStyle.current.centredOnLine())
    KbdBadge(key)
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> spatialSpec(slow: Boolean = false): FiniteAnimationSpec<T> =
  if (slow) MaterialTheme.motionScheme.slowSpatialSpec() else MaterialTheme.motionScheme.defaultSpatialSpec()

@Composable
fun rememberPulse(key: Any?, scale: Float): Animatable<Float, AnimationVector1D> {
  val pulse = remember { Animatable(1f) }
  val swell = spatialSpec<Float>()
  LaunchedEffect(key) {
    pulse.snapTo(1f)
    if (key == null) return@LaunchedEffect
    pulse.animateTo(scale, swell)
    pulse.animateTo(1f, swell)
  }
  return pulse
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> effectsSpec(slow: Boolean = false): FiniteAnimationSpec<T> =
  if (slow) MaterialTheme.motionScheme.slowEffectsSpec() else MaterialTheme.motionScheme.defaultEffectsSpec()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> spatialFadeSpec(): FiniteAnimationSpec<T> {
  val stiffness = (MaterialTheme.motionScheme.defaultSpatialSpec<T>() as? SpringSpec<T>)?.stiffness ?: Spring.StiffnessMediumLow
  return spring(Spring.DampingRatioNoBouncy, stiffness)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun spatialBoundsTransform(slow: Boolean = true): BoundsTransform {
  val spatial = spatialSpec<Rect>(slow)
  return remember(spatial) {
    val spec = (spatial as? SpringSpec<Rect>)?.let { spring(it.dampingRatio, it.stiffness, BOUNDS_LANDED) } ?: spatial
    BoundsTransform { _, _ -> spec }
  }
}

private val BOUNDS_LANDED = Rect(1f, 1f, 1f, 1f)

@Composable
fun ScrollToTopButton(
  visible: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  replaced: Boolean = false,
  fade: Boolean = false,
) {
  AnimatedVisibility(
    visible = visible && !replaced,
    enter = if (fade) fadeIn(effectsSpec()) else scaleIn(spatialSpec()),
    exit = if (fade) fadeOut(effectsSpec()) else scaleOut(spatialSpec()),
    modifier = modifier,
  ) {
    if (compact) {
      PillSizedTop(onClick = onClick)
    } else {
      SmallExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(arrow_upward, contentDescription = null) },
        text = { Text("Top") },
      )
    }
  }
}

@Composable
private fun PillSizedTop(onClick: () -> Unit) {
  val presses = remember { MutableInteractionSource() }
  Box(
    Modifier
      .size(BottomRowLayout.PieceTarget)
      .clickable(presses, indication = null, role = Role.Button, onClick = onClick)
      .semantics { contentDescription = "Top" },
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      shadowElevation = FabElevation,
      modifier = Modifier.size(BottomRowLayout.PieceDrawn),
    ) {
      Box(Modifier.clip(CircleShape).indication(presses, ripple()), contentAlignment = Alignment.Center) {
        Icon(arrow_upward, contentDescription = null, modifier = Modifier.size(20.dp))
      }
    }
  }
}


fun Modifier.entrance(
  shown: Boolean,
  delayMillis: Long = 0,
  offsetX: Dp = 0.dp,
  offsetY: Dp = 0.dp,
  fromScale: Float = 1f,
  fast: Boolean = false,
): Modifier = composed {
  val progress = remember { Animatable(if (shown) 1f else 0f) }
  val inSpec = if (fast) fastSpatialSpec<Float>() else spatialSpec<Float>()
  val outSpec = fastSpatialSpec<Float>()
  LaunchedEffect(shown) {
    if (shown) {
      delay(delayMillis)
      progress.animateTo(1f, inSpec)
    } else {
      progress.animateTo(0f, outSpec)
    }
  }
  graphicsLayer {
    val t = progress.value
    translationX = (1 - t) * offsetX.toPx()
    translationY = (1 - t) * offsetY.toPx()
    val scale = fromScale + (1 - fromScale) * t
    scaleX = scale
    scaleY = scale
    alpha = (t / 0.4f).coerceIn(0f, 1f)
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()
