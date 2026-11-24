package dev.marzahn.sankaku.features.grid

import kotlin.test.Test
import kotlin.test.assertEquals

class PillFadeTest {
  @Test
  fun withRoomToSpareItFadesOverTheLastStretch() {
    assertEquals(1f, pillShown(room = 300f, roomAtPeek = 300f, fade = 120f))
    assertEquals(1f, pillShown(room = 120f, roomAtPeek = 300f, fade = 120f))
    assertEquals(0.5f, pillShown(room = 60f, roomAtPeek = 300f, fade = 120f))
    assertEquals(0f, pillShown(room = 0f, roomAtPeek = 300f, fade = 120f))
  }

  @Test
  fun atPeekItsAlwaysWhole() {
    assertEquals(1f, pillShown(room = 90f, roomAtPeek = 90f, fade = 120f))
    assertEquals(0.5f, pillShown(room = 45f, roomAtPeek = 90f, fade = 120f))
    assertEquals(0f, pillShown(room = 0f, roomAtPeek = 90f, fade = 120f))
  }

  @Test
  fun withNoRoomAtPeekItsAway() {
    assertEquals(0f, pillShown(room = 0f, roomAtPeek = 0f, fade = 120f))
    assertEquals(0f, pillShown(room = -10f, roomAtPeek = -10f, fade = 120f))
  }
}
