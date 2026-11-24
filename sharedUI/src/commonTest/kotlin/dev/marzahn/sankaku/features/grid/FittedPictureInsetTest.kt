package dev.marzahn.sankaku.features.grid

import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FittedPictureInsetTest {
  @Test
  fun thePictureEndsAtThePillRowsTop() {
    assertEquals(32.dp + BottomRowLayout.Height / 2, fittedPictureInset())
    assertEquals(60.dp, fittedPictureInset())
  }

  @Test
  fun aVideoAlsoKeepsClearOfItsSeekRow() {
    assertEquals(60.dp, SEEK_ROOM)
    assertEquals(120.dp, fittedPictureInset(seekRoom = SEEK_ROOM))
  }

  @Test
  fun atACompactHeightTheControlsFloatOverThePicture() {
    assertEquals(0.dp, fittedPictureInset(compactHeight = true))
    assertEquals(0.dp, fittedPictureInset(seekRoom = SEEK_ROOM, compactHeight = true))
  }

  @Test
  fun aCompactHeightIsUnder480Dp() {
    assertTrue(isCompactHeight(412.dp))
    assertTrue(isCompactHeight(479.dp))
    assertFalse(isCompactHeight(480.dp))
    assertFalse(isCompactHeight(915.dp))
  }
}
