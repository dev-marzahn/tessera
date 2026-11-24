package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostViewerLayoutTest {
  @Test
  fun theSplitHoldsAtItsEnds() {
    assertEquals(0f, splitFraction(-0.09f))
    assertEquals(1f, splitFraction(1.03f))
    assertEquals(0.4f, splitFraction(0.4f))
  }

  @Test
  fun mergingBackNeverPassesZero() {
    val bouncy = spring<Float>(dampingRatio = 0.6f, stiffness = 800f)
    val merge = bouncy.withoutBounce()
    assertEquals(Spring.DampingRatioNoBouncy, (merge as SpringSpec).dampingRatio)
    assertEquals(800f, merge.stiffness)

    val samples = (0..100).map { sample(merge, it * 10L) }
    assertTrue(samples.all { it >= 0f }, "went below 0: ${samples.min()}")
    assertTrue(samples.last() < 0.01f)
    assertTrue((0..100).any { sample(bouncy, it * 10L) < -0.05f }, "the bouncy spring should overshoot")
  }

  @Test
  fun closingMergesQuickly() {
    val close = spring<Float>(dampingRatio = 0.6f, stiffness = 800f).withoutBounce(stiffnessScale = 4f) as SpringSpec
    assertTrue(sample(close, 100) < 0.05f)
    assertTrue((0..50).all { sample(close, it * 10L) >= 0f })
  }

  @Test
  fun theInfoPanelGrowsFromTheOnePanelOnlyWhenOpeningOrClosing() {
    assertEquals(800, infoPanelHeight(800, 500, split = 0f, followsSplit = true))
    assertEquals(650, infoPanelHeight(800, 500, split = 0.5f, followsSplit = true))
    assertEquals(500, infoPanelHeight(800, 500, split = 1f, followsSplit = true))
    assertEquals(500, infoPanelHeight(800, 500, split = 0.1f, followsSplit = false))
    assertEquals(500, infoPanelHeight(800, 500, split = 0.9f, followsSplit = false))
  }

  @Test
  fun partingKeepsTheImagePanelLaidOutTheSizeOfBoth() {
    val merged = cardLayout(1000.dp, split = 0.02f, parting = true, collapsed = false, corner = 28.dp)
    assertFalse(merged.infoShown)
    assertEquals(1000.dp, merged.panelWidth)
    assertEquals(0.dp, merged.panelEndInset)
    assertEquals(28.dp, merged.panelEndCorner)
    assertEquals(690.dp, merged.contentWidth)

    val parted = cardLayout(1000.dp, split = 1f, parting = true, collapsed = false, corner = 28.dp)
    assertEquals(1000.dp, parted.panelWidth)
    assertEquals(310.dp, parted.panelEndInset)
    assertEquals(700.dp, parted.infoX)
    assertEquals(300.dp, parted.infoWidth)
    assertEquals(8.dp, parted.panelEndCorner)
    assertEquals(690.dp, parted.contentWidth)
  }

  @Test
  fun partingOpensTheGapFromTheSeamAndFollowsTheOvershoot() {
    val start = cardLayout(1000.dp, split = 0.06f, parting = true, collapsed = false, corner = 28.dp)
    assertTrue(start.infoShown)
    assertEquals(0.6f, (start.infoX - (1000.dp - start.panelEndInset)).value, 0.001f)
    assertEquals(1000.dp, start.infoX + start.infoWidth)

    val over = cardLayout(1000.dp, split = 1.1f, parting = true, collapsed = false, corner = 28.dp)
    assertEquals(11f, (over.infoX - (1000.dp - over.panelEndInset)).value, 0.001f)
    assertEquals(1000.dp, over.infoX + over.infoWidth)
  }

  @Test
  fun partedAndToggledLayoutsMeetWhenOpen() {
    val parted = cardLayout(1000.dp, split = 1f, parting = true, collapsed = false, corner = 28.dp)
    val slid = cardLayout(1000.dp, split = 1f, parting = false, collapsed = false, corner = 28.dp)
    assertEquals(parted.panelWidth - parted.panelEndInset, slid.panelWidth - slid.panelEndInset)
    assertEquals(parted.infoX, slid.infoX)
    assertEquals(parted.infoWidth, slid.infoWidth)
    assertEquals(parted.panelEndCorner, slid.panelEndCorner)
    assertEquals(parted.infoInnerCorner, slid.infoInnerCorner)
    assertEquals(parted.contentWidth, slid.contentWidth)
  }

  @Test
  fun collapsedTheImageGetsTheWholeCard() {
    assertEquals(1000.dp, cardLayout(1000.dp, split = 0f, parting = true, collapsed = true, corner = 28.dp).contentWidth)
    assertEquals(1000.dp, cardLayout(1000.dp, split = 0f, parting = false, collapsed = true, corner = 28.dp).contentWidth)
  }

  @Test
  fun onlyTheOverlaySteps() {
    assertTrue(ViewerMode.Overlay.steps)
    assertFalse(ViewerMode.Tab.steps)
  }

  @Test
  fun thePositionCountsWhatIsLoaded() {
    assertEquals("1 of 48+", positionLabel(0, 48, hasMore = true))
    assertEquals("48 of 48", positionLabel(47, 48, hasMore = false))
  }

  private fun sample(spec: SpringSpec<Float>, millis: Long): Float {
    val vectorized = spec.vectorize(Float.VectorConverter)
    return vectorized.getValueFromNanos(millis * 1_000_000, AnimationVector1D(1f), AnimationVector1D(0f), AnimationVector1D(0f)).value
  }
}
