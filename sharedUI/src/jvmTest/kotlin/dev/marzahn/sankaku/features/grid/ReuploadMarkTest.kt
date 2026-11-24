package dev.marzahn.sankaku.features.grid

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.marzahn.sankaku.queue.SeenStore
import org.koin.core.context.GlobalContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReuploadMarkTest {
  private val fixture = GridScreenFixture(images = true, pictures = mapOf(2 to 20, 5 to 20, 3 to 30))

  @BeforeTest
  fun start() = fixture.start()

  @AfterTest
  fun stop() = fixture.stop()

  private fun ComposeUiTest.description(index: Int): String =
    onNodeWithTag(gridCellTag(index)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.first()

  private fun ComposeUiTest.keepFromMenu(index: Int) {
    onNodeWithTag(gridCellTag(index)).performMouseInput { rightClick() }
    waitForIdle()
    onNodeWithText("Open in background tab").performClick()
    waitForIdle()
  }

  @Test
  fun aReuploadOfAKeptPostIsMarkedAndKeepingItKeepsNoDuplicate() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      assertFalse("In tab" in description(4), "nothing is kept yet")

      keepFromMenu(1)
      assertEquals(listOf("fixture_tag", "fixture-2"), queue())

      waitUntil(timeoutMillis = 5_000) { "In tab" in description(4) }
      assertFalse("In tab" in description(2), "another picture isn't marked")
      assertNotNull(GlobalContext.get().get<SeenStore>().picture("fixture-2"))

      keepFromMenu(4)
      assertEquals(listOf("fixture_tag", "fixture-2"), queue())
      assertTrue("Already in tab 2" in description(4), description(4))
    }
  }
}
