package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.arrow_back

data class Shortcut(val key: Key, val label: String, val alias: Key? = null) {
  fun matches(key: Key): Boolean = key == this.key || key == alias
}

class OncePerPress {
  private var held: Key? = null

  fun letGo() {
    held = null
  }

  fun isNewPress(event: KeyEvent): Boolean = when (event.type) {
    KeyEventType.KeyDown -> (event.key != held).also { held = event.key }
    KeyEventType.KeyUp -> false.also { if (event.key == held) held = null }
    else -> false
  }
}

val KeyEvent.isCommand: Boolean get() = isMetaPressed || isCtrlPressed

val KeyEvent.isPlain: Boolean get() = !isCommand && !isAltPressed && !isShiftPressed

val KeyEvent.tabStep: Boolean?
  get() = when {
    !isShiftPressed || isCommand || isAltPressed -> null
    key == Shortcuts.PreviousTab.key -> false
    key == Shortcuts.NextTab.key -> true
    else -> null
  }

object Shortcuts {
  val Back = Shortcut(Key.Escape, "Esc")
  val Favourite = Shortcut(Key.F, "F")
  val Previous = Shortcut(Key.DirectionLeft, "←", alias = Key.A)
  val Next = Shortcut(Key.DirectionRight, "→", alias = Key.D)

  val Up = Shortcut(Key.DirectionUp, "↑", alias = Key.W)
  val Down = Shortcut(Key.DirectionDown, "↓", alias = Key.S)
  val Details = Shortcut(Key.I, "I")
  val Search = Shortcut(Key.Slash, "/")

  val Find = Shortcut(Key.F, "⌘F")

  val ReopenTab = Shortcut(Key.Z, "Z")

  val CloseTab = Shortcut(Key.X, "X")

  val PreviousTab = Shortcut(Key.A, "⇧A")
  val NextTab = Shortcut(Key.D, "⇧D")

  val ClearViewed = Shortcut(Key.E, "⌘⇧E")

  val NewTab = Shortcut(Key.T, "⌘T")
  val CloseTabCommand = Shortcut(Key.W, "⌘W")

  val ReopenTabCommand = Shortcut(Key.T, "⌘⇧T")

  val NextTabCommand = Shortcut(Key.Tab, "⌃Tab")
  val PreviousTabCommand = Shortcut(Key.Tab, "⌃⇧Tab")

  val Refresh = Shortcut(Key.R, "⌘R", alias = Key.F5)

  val Help = Shortcut(Key.Slash, "?")

  val Sidebar = Shortcut(Key.G, "G")

  val TabSwitcher = Shortcut(Key.C, "C")

  val SwitcherMode = Shortcut(Key.Q, "Q")

  val CloseBlock = Shortcut(Key.X, "⇧X")

  val TagList = Shortcut(Key.T, "T")

  val OpenInTab = Shortcut(Key.E, "E")

  val OpenHere = Shortcut(Key.Spacebar, "Space")

  val CloseViewer = OpenHere

  val Include = Shortcut(Key.E, "E")
  val Exclude = Shortcut(Key.Q, "Q")
  val Remove = Shortcut(Key.Backspace, "⌫")

  val Menu = Shortcut(Key.R, "R")

  val PlayPause = Shortcut(Key.Spacebar, "Space")
  val Mute = Shortcut(Key.M, "M")
  val Scrub = Shortcut(Key.V, "V")

  val Vote = Shortcut(Key.One, "1–5")

  fun voteFor(key: Key): Int? = when (key) {
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    else -> null
  }
}

@Stable
class HeldKeys {
  private val held = mutableSetOf<Key>()
  private var repeat: Key? = null

  fun repeating(key: Key): Boolean = repeat == key

  fun forget() {
    held.clear()
    repeat = null
  }

  val modifier: Modifier = Modifier.onPreviewKeyEvent { event ->
    when (event.type) {
      KeyEventType.KeyDown -> repeat = event.key.takeIf { !held.add(it) }
      KeyEventType.KeyUp -> {
        held.remove(event.key)
        if (repeat == event.key) repeat = null
      }
    }
    false
  }
}

@Composable
fun rememberHeldKeys(): HeldKeys {
  val keys = remember { HeldKeys() }
  val focused = LocalWindowInfo.current.isWindowFocused
  LaunchedEffect(focused) { if (!focused) keys.forget() }
  return keys
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier, alsoKey: Shortcut? = null) {
  val height = ButtonDefaults.ExtraSmallContainerHeight
  TextButton(
    onClick = onClick,
    shapes = ButtonDefaults.shapes(),
    contentPadding = ButtonDefaults.contentPaddingFor(height),
    modifier = modifier.heightIn(min = height),
  ) {
    Icon(arrow_back, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
    Text("Back", style = ButtonDefaults.textStyleFor(height))
    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
    KbdBadge(Shortcuts.Back.label)
    if (alsoKey != null) {
      Spacer(Modifier.width(2.dp))
      KbdBadge(alsoKey.label)
    }
  }
}

@Composable
fun KeyFocusHolder(modifier: Modifier = Modifier) {
  val focus = remember { FocusRequester() }
  val returns = LocalKeyFocus.current.returns
  LaunchedEffect(returns) { focus.requestFocus() }
  Box(modifier.size(0.dp).focusRequester(focus).focusable())
}

@Stable
class KeyFocus {
  var returns by mutableIntStateOf(0)
    private set

  fun returnToContent() {
    returns++
  }
}

val LocalKeyFocus = staticCompositionLocalOf { KeyFocus() }
