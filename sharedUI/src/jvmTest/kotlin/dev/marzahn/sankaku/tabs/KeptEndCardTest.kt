package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class KeptEndCardTest {
  private val tabs = TabsController()
  private val upNext = noUpNext(tabs)
  private val seen = SeenStore(InMemoryTextFile())
  private val clearViewed = ClearViewed(tabs, seen, ClearedUndoOnTopLine(ClosedTabUndo(tabs), Messages()))

  init {
    startKoin {
      modules(
        module {
          single { tabs }
          single<TabPosts> { TabPosts.None }
          single { PrivacySettings(InMemoryTextFile()) }
          single { clearViewed }
        },
      )
    }
  }

  @AfterTest
  fun stop() {
    stopKoin()
  }

  private fun swipeThroughTheKeptPosts() {
    val source = tabs.openSource("artist_a")
    val kept = listOf("p1", "p2", "p3", "p4").map { tabs.keepInto(source, it) }
    tabs.startKeptRun(kept.map { it.id }, kept.first().id)
    repeat(kept.size) { upNext.forward() }
    assertEquals(UpNextCard.KeptEnd, upNext.card)
    assertTrue(kept.all { it.later })
  }

  private fun ComposeUiTest.showCard(fontScale: Float = 1f) {
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(2f, fontScale = fontScale)) {
        SankakuTheme { Box(Modifier.size(PHONE, 640.dp)) { UpNextCardView(state = upNext, leftovers = null) } }
      }
    }
    waitForIdle()
  }

  @Test
  fun theEndOfASwipedThroughRunOffersToClearTheLaterPostsAndClearsThem() = runComposeUiTest {
    swipeThroughTheKeptPosts()
    showCard()

    onNodeWithText("Go through later · 4").assertExists()
    onNodeWithContentDescription(clearViewedLabel(4)).performClick()
    waitForIdle()

    assertEquals(emptyList(), tabs.tabs.value.filter { it.kind.isKept })
  }

  @Test
  fun itsActionsAreCustomActionsOnTheTally() = runComposeUiTest {
    swipeThroughTheKeptPosts()
    showCard()

    val heading = onNode(isHeading()).fetchSemanticsNode()
    val actions = heading.config[SemanticsActions.CustomActions].map { it.label }
    assertTrue(actions.containsAll(listOf(GO_THROUGH_LATER, "Clear viewed", TO_THE_QUEUE)), "$actions")
  }

  @Test
  fun itsThreeButtonsKeepTheirLabelsOnOneLineInsideTheCardOnTheNarrowestPhone() {
    for (scale in listOf(1f, 1.3f)) runComposeUiTest {
      swipeThroughTheKeptPosts()
      showCard(scale)

      val card = onNodeWithTag(END_CARD).getUnclippedBoundsInRoot()
      listOf("Go through later · 4", "Clear viewed · 4", TO_THE_QUEUE).forEach { label ->
        val button = onNodeWithText(label, useUnmergedTree = true)
        val bounds = button.getUnclippedBoundsInRoot()
        assertTrue(bounds.left >= card.left && bounds.right <= card.right, "$label at $scale× sticks out: $bounds of $card")
        val layout = mutableListOf<TextLayoutResult>()
        button.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        val text = layout.single()
        assertEquals(1, text.lineCount, "$label wraps at $scale×")
        assertTrue(text.multiParagraph.height <= text.size.height + 0.5f, "$label is cut at $scale×")
      }
    }
  }

  private companion object {
    val PHONE = 360.dp
  }
}
