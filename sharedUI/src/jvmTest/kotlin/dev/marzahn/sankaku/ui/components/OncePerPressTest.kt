package dev.marzahn.sankaku.ui.components

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalComposeUiApi::class)
class OncePerPressTest {
  private val presses = OncePerPress()

  private fun news(vararg events: Pair<Key, KeyEventType>) = events.map { (key, type) -> presses.isNewPress(KeyEvent(key, type)) }

  @Test
  fun aHeldKeyIsOnePress() {
    assertEquals(
      listOf(true, false, false, false, true, false),
      news(Key.F to KeyEventType.KeyDown, Key.F to KeyEventType.KeyDown, Key.F to KeyEventType.KeyDown, Key.F to KeyEventType.KeyUp, Key.F to KeyEventType.KeyDown, Key.F to KeyEventType.KeyUp),
    )
  }

  @Test
  fun losingTheFocusLetsGo() {
    assertEquals(listOf(true), news(Key.F to KeyEventType.KeyDown))
    presses.letGo()
    assertEquals(listOf(true), news(Key.F to KeyEventType.KeyDown))
  }

  @Test
  fun anotherKeyLetsGoInCaseTheReleaseWentElsewhere() {
    assertEquals(
      listOf(true, true, true),
      news(Key.F to KeyEventType.KeyDown, Key.Three to KeyEventType.KeyDown, Key.F to KeyEventType.KeyDown),
    )
  }
}
