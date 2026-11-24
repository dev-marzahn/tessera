package dev.marzahn.sankaku.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.api.ErrorAction
import dev.marzahn.sankaku.api.advice
import com.google.fonts.icons.cloud_off
import com.google.fonts.icons.error
import com.google.fonts.icons.favorite
import com.google.fonts.icons.favorite_filled
import com.google.fonts.icons.wifi
import com.google.fonts.icons.wifi_off
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLoadingIndicator(
  modifier: Modifier = Modifier,
  size: Dp = 48.dp,
  color: Color = LoadingIndicatorDefaults.indicatorColor,
) {
  LoadingIndicator(modifier.size(size), color = color)
}

val LargeIndicatorSize = 96.dp

@Composable
fun FavouriteButton(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FavouriteToggle(checked, onCheckedChange)
    KbdBadge(Shortcuts.Favourite.label)
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavouriteBadgeButton(favourited: Boolean, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
  val corner = TransformOrigin(1f, 0f)
  AnimatedVisibility(
    visible = favourited || expanded,
    enter = scaleIn(fastSpatialSpec(), transformOrigin = corner) + fadeIn(effectsSpec()),
    exit = scaleOut(fastSpatialSpec(), transformOrigin = corner) + fadeOut(effectsSpec()),
    modifier = modifier,
  ) {
    val size by animateDpAsState(if (expanded) 40.dp else 26.dp, fastSpatialSpec())
    val iconSize by animateDpAsState(if (expanded) 24.dp else 16.dp, fastSpatialSpec())
    val container by animateColorAsState(
      if (favourited) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
      effectsSpec(),
    )
    val content by animateColorAsState(
      if (favourited) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
      effectsSpec(),
    )
    val scale = rememberPop(favourited)
    Surface(
      shape = CircleShape,
      color = container,
      contentColor = content,
      shadowElevation = 2.dp,
      modifier = Modifier
        .graphicsLayer {
          scaleX = scale.value
          scaleY = scale.value
          transformOrigin = corner
        }
        .size(size)
        .then(
          if (expanded) Modifier.toggleable(value = favourited, role = Role.Checkbox, onValueChange = { onToggle() })
          else Modifier
        ),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = if (favourited) favorite_filled else favorite,
          contentDescription = when {
            !expanded -> "Favourited"
            favourited -> "Unfavourite"
            else -> "Favourite"
          },
          modifier = Modifier.size(iconSize),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberPop(checked: Boolean): Animatable<Float, AnimationVector1D> {
  val scale = remember { Animatable(1f) }
  var first by remember { mutableStateOf(true) }
  val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
  LaunchedEffect(checked) {
    if (first) {
      first = false
      return@LaunchedEffect
    }
    scale.animateTo(if (checked) 1.25f else 0.8f, spring)
    scale.animateTo(1f, spring)
  }
  return scale
}

@Composable
fun HeartToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, rejected: Int = 0, enabled: Boolean = true) {
  FavouriteToggle(checked, onCheckedChange, modifier.rejected(rejected, CircleShape), enabled)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavouriteToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  val scale = rememberPop(checked)
  IconToggleButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    enabled = enabled,
    colors = IconButtonDefaults.iconToggleButtonColors(checkedContentColor = MaterialTheme.colorScheme.primary),
    shapes = IconButtonDefaults.toggleableShapes(),
    modifier = modifier.graphicsLayer {
      scaleX = scale.value
      scaleY = scale.value
    },
  ) {
    Icon(
      imageVector = if (checked) favorite_filled else favorite,
      contentDescription = if (checked) "Unfavourite" else "Favourite",
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  navigationIcon: @Composable () -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
) {
  TopAppBar(
    title = {
      if (subtitle == null) {
        Text(title)
      } else {
        Column {
          Text(title)
          Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    },
    navigationIcon = navigationIcon,
    actions = actions,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ErrorState(
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  retryEnabledAt: Instant? = null,
) {
  val secondsLeft = countdownTo(retryEnabledAt)
  MessageState(error, MaterialTheme.colorScheme.error, message, modifier) {
    Button(onClick = onRetry, enabled = secondsLeft <= 0, shapes = ButtonDefaults.shapes()) {
      Text(if (secondsLeft > 0) "Retry in $secondsLeft s" else "Retry")
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ApiErrorState(
  failure: Throwable,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  onEditSearch: (() -> Unit)? = null,
  onSignIn: (() -> Unit)? = null,
  signedIn: Boolean = true,
) {
  val advice = remember(failure, signedIn) { failure.advice(signedIn) }
  val retryEnabledAt = remember(failure) { advice.retryAfterSeconds?.let { Clock.System.now() + it.seconds } }
  val secondsLeft = countdownTo(retryEnabledAt)
  val retry = ErrorButton(if (secondsLeft > 0) "Retry in $secondsLeft s" else "Retry", onRetry, enabled = secondsLeft <= 0)
  val buttons = advice.actions.mapNotNull { action ->
    when (action) {
      ErrorAction.Retry -> retry
      ErrorAction.EditSearch -> onEditSearch?.let { ErrorButton("Edit search", it) }
      ErrorAction.SignIn -> onSignIn?.let { ErrorButton("Sign in", it) }
    }
  }.ifEmpty { listOf(retry) }
  MessageState(error, MaterialTheme.colorScheme.error, advice.sentence, modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      buttons.forEachIndexed { index, button ->
        if (index == 0) {
          Button(onClick = button.onClick, enabled = button.enabled, shapes = ButtonDefaults.shapes()) { Text(button.label) }
        } else {
          OutlinedButton(onClick = button.onClick, enabled = button.enabled, shapes = ButtonDefaults.shapes()) { Text(button.label) }
        }
      }
    }
  }
}

private class ErrorButton(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)

@Composable
private fun countdownTo(instant: Instant?): Long {
  var secondsLeft by remember(instant) { mutableLongStateOf(secondsUntil(instant)) }
  LaunchedEffect(instant) {
    while (secondsLeft > 0) {
      delay(1.seconds)
      secondsLeft = secondsUntil(instant)
    }
  }
  return secondsLeft
}

@Composable
private fun MessageState(
  icon: ImageVector,
  tint: Color,
  message: String,
  modifier: Modifier = Modifier,
  below: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
    Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    below()
  }
}

private fun secondsUntil(instant: Instant?): Long =
  instant?.let { (it - Clock.System.now()).inWholeSeconds.coerceAtLeast(0) } ?: 0

enum class ConnectivityBannerState(val icon: ImageVector?, val text: String) {
  Rejected(null, "Sankaku is turning this app away right now · try again later"),

  Offline(cloud_off, "Offline · retries when you're back"),

  BackOnline(wifi, "Back online · reloading"),
}

val BACK_ONLINE_SHOWN = 2.seconds

@Composable
fun connectivityBannerState(offline: Boolean, unreachable: Boolean, rejected: Boolean): ConnectivityBannerState? {
  var wasUnreachable by remember { mutableStateOf(offline && unreachable) }
  var holding by remember { mutableStateOf(false) }
  LaunchedEffect(offline, unreachable) {
    if (!offline && wasUnreachable) {
      holding = true
      wasUnreachable = false
      try {
        delay(BACK_ONLINE_SHOWN)
      } finally {
        holding = false
      }
    } else {
      wasUnreachable = offline && unreachable
    }
  }
  return when {
    rejected -> ConnectivityBannerState.Rejected
    offline -> ConnectivityBannerState.Offline
    holding || wasUnreachable -> ConnectivityBannerState.BackOnline
    else -> null
  }
}

@Composable
fun ConnectivityBanner(state: ConnectivityBannerState?, modifier: Modifier = Modifier) {
  val last = remember { object { var state = ConnectivityBannerState.Offline } }
  SideEffect { if (state != null) last.state = state }
  AnimatedVisibility(
    state != null,
    modifier,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
  ) {
    val shown = state ?: last.state
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (shown) {
      ConnectivityBannerState.Rejected -> colors.errorContainer to colors.onErrorContainer
      ConnectivityBannerState.Offline -> colors.inverseSurface to colors.inverseOnSurface
      ConnectivityBannerState.BackOnline -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Surface(
      color = animateColorAsState(container).value,
      contentColor = animateColorAsState(content).value,
      modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
      Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        shown.icon?.let { Icon(it, contentDescription = null, Modifier.size(18.dp)) }
        Text(shown.text, style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GridOfflineState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
  MessageState(wifi_off, MaterialTheme.colorScheme.onSurfaceVariant, "You're offline", modifier) {
    Text(
      "This grid loads by itself when the connection is back.",
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text("Retry now") }
  }
}

@Composable
fun TagChip(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  AssistChip(onClick = onClick, label = { Text(name.replace('_', ' ')) }, modifier = modifier)
}
