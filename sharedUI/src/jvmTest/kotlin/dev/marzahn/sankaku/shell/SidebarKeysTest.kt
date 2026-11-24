package dev.marzahn.sankaku.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.runComposeUiTest
import com.google.fonts.icons.search
import com.google.fonts.icons.shield
import dev.marzahn.sankaku.features.auth.AccountRow
import dev.marzahn.sankaku.features.settings.SwitchRow
import dev.marzahn.sankaku.ui.SHORT_WINDOW_MS
import dev.marzahn.sankaku.ui.components.ModalNavigationRail
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.keyRing
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class SidebarKeysTest {
  private var open by mutableStateOf(true)
  private var gOnly by mutableStateOf(false)
  private val opened = mutableListOf<String>()
  private val openedInNewTab = mutableListOf<String>()
  private var signOuts = 0

  private fun ComposeUiTest.showRail(current: String?, byKey: Boolean = true) {
    var held by mutableStateOf(byKey)
    setContent {
      SankakuTheme {
        ModalNavigationRail(
          open,
          onClose = { open = false },
          containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
          closeKey = Shortcuts.Sidebar.key,
          focusColumn = !byKey,
          closeKeyHeld = held,
          onCloseKeyUp = { held = false },
        ) {
          CompositionLocalProvider(LocalOpenedByKey provides (open && byKey)) {
            for (label in listOf("Browse", "For you", "Popular")) {
              Entry(
                label,
                search,
                onOpen = { opened += label },
                onOpenInNewTab = { openedInNewTab += label },
                selected = label == current,
                startHere = label == "Browse" && current == null,
              )
            }
            SwitchRow("Locked", help = null, checked = true, onCheckedChange = {}, enabled = false, icon = shield)
            SwitchRow(
              "G only",
              help = null,
              checked = gOnly,
              onCheckedChange = { gOnly = it },
              modifier = Modifier.keyRing(CircleShape),
              icon = shield,
            )
            Entry("Settings", search, onOpen = { opened += "Settings" }, onOpenInNewTab = { openedInNewTab += "Settings" })
            AccountRow(
              "someone",
              avatarUrl = null,
              open = open,
              onSignOut = { signOuts++ },
              modifier = Modifier.keyRing(CircleShape),
              windowMillis = SHORT_WINDOW_MS,
            )
          }
        }
      }
    }
    waitForIdle()
  }

  private fun ComposeUiTest.press(key: Key) = press(listOf(key))

  private fun ComposeUiTest.press(keys: List<Key>) {
    for (key in keys) {
      onNodeWithText("Browse").performKeyInput { pressKey(key) }
      waitForIdle()
    }
  }

  @Test
  fun focusStartsOnTheCurrentTabsEntry() = runComposeUiTest {
    showRail(current = "Popular")
    onNodeWithText("Popular").assertIsFocused()
    onNodeWithText("Browse").assertIsNotFocused()
  }

  @Test
  fun focusStartsOnTheFirstEntryWhenNoneIsCurrent() = runComposeUiTest {
    showRail(current = null)
    onNodeWithText("Browse").assertIsFocused()
  }

  @Test
  fun wsMoveThroughEveryEntryAndStopAtTheEnds() = runComposeUiTest {
    showRail(current = null)
    press(Key.W)
    onNodeWithText("Browse").assertIsFocused()
    press(Key.S)
    onNodeWithText("For you").assertIsFocused()
    press(Key.DirectionDown)
    onNodeWithText("Popular").assertIsFocused()
    press(Key.S)
    onNodeWithText("G only").assertIsFocused()
    press(Key.S)
    onNodeWithText("Settings").assertIsFocused()
    press(Key.S)
    onNodeWithContentDescription("Sign out").assertIsFocused()
    press(Key.S)
    onNodeWithContentDescription("Sign out").assertIsFocused()
    press(listOf(Key.W, Key.DirectionUp))
    onNodeWithText("G only").assertIsFocused()
  }

  @Test
  fun spaceOpensHereAndEInANewTabLeavingTheSidebarOpen() = runComposeUiTest {
    showRail(current = "For you")
    press(Key.E)
    assertEquals(listOf("For you"), openedInNewTab)
    assertEquals(true, open)
    press(listOf(Key.S, Key.Spacebar))
    assertEquals(listOf("Popular"), opened)
    press(listOf(Key.S, Key.Spacebar))
    assertEquals(true, gOnly)
  }

  @Test
  fun signOutTakesTwoSpaces() = runComposeUiTest {
    showRail(current = "Popular")
    press(listOf(Key.S, Key.S, Key.S))
    mainClock.autoAdvance = false
    press(Key.Spacebar)
    mainClock.advanceTimeBy(500)
    onNodeWithText("Sign out? Tap again").assertIsFocused()
    assertEquals(0, signOuts)
    press(Key.Spacebar)
    mainClock.advanceTimeBy(500)
    assertEquals(1, signOuts)
    press(Key.S)
    onNodeWithText("Browse").assertIsFocused()
  }

  @Test
  fun gClosesItOnceTheGThatOpenedItIsLetGo() = runComposeUiTest {
    showRail(current = "Popular")
    onNodeWithText("Browse").performKeyInput { keyDown(Key.G) }
    waitForIdle()
    assertEquals(true, open)
    onNodeWithText("Browse").performKeyInput { keyUp(Key.G) }
    waitForIdle()
    onNodeWithText("Browse").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.G) } }
    waitForIdle()
    assertEquals(true, open)
    press(Key.G)
    assertFalse(open)
    assertEquals(emptyList(), opened)
  }

  @Test
  fun openedWithTheMenuButtonWEntersTheEntriesFromTheRail() = runComposeUiTest {
    showRail(current = "Popular", byKey = false)
    onNodeWithText("Popular").assertIsNotFocused()
    press(Key.S)
    onNodeWithText("Browse").assertIsFocused()
    press(Key.G)
    assertFalse(open)
  }

  @Test
  fun escClosesIt() = runComposeUiTest {
    showRail(current = "Popular")
    press(Key.Escape)
    assertFalse(open)
  }
}
