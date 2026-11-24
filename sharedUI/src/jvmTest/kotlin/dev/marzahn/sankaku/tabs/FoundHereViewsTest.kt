package dev.marzahn.sankaku.tabs

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FoundHereViewsTest {
  private val tabs = TabsController()
  private val navigator = TabsNavigator(tabs)
  private val deck = SortDeck(tabs, ClosedTabUndo(tabs))
  private val finds = BlockFinds(tabs, deck)
  private val artistX = Route.Grid(listOf("artist_x"))
  private val landscape = Route.Grid(listOf("landscape"))

  private val x: TabState = run {
    tabs.open(landscape, background = true)
    navigator.goTo(artistX)
    tabs.activeTab.markTouched(artistX)
    navigator.openInNewTab(Route.Grid(listOf("scenery")))
    navigator.openInNewTab(Route.Grid(listOf("artist_y")))
    tabs.activeTab
  }

  private fun find(title: String) = tabs.tabs.value.single { it.title == title }

  @Test
  fun theChipCountsTheFindsAndItsSheetListsThem() = runComposeUiTest {
    setContent { SankakuTheme { FoundHereChip(x, artistX, visible = true, touch = true, tabs = tabs, deck = deck) } }

    onNodeWithText("$FOUND_HERE · 2").assertIsDisplayed()
    onNodeWithTag(FOUND_HERE_CHIP).performClick()

    onNodeWithTag(FOUND_HERE_SHEET).assertIsDisplayed()
    onNodeWithText("Found on Artist X").assertIsDisplayed()
    onNodeWithText("2 searches opened from here · in the queue").assertIsDisplayed()
    onNodeWithText("Scenery").assertIsDisplayed()
    onNodeWithText("from the grid · item 3").assertIsDisplayed()
  }

  @Test
  fun aFindTappedInTheSheetIsShown() = runComposeUiTest {
    setContent { SankakuTheme { FoundHereChip(x, artistX, visible = true, touch = true, tabs = tabs, deck = deck) } }
    onNodeWithTag(FOUND_HERE_CHIP).performClick()

    onNodeWithText("Artist Y").performClick()
    waitForIdle()

    runOnIdle { assertEquals(find("Artist Y"), tabs.activeTab) }
  }

  @Test
  fun sortTheseRunsTheDeckOverJustThose() = runComposeUiTest {
    setContent { SankakuTheme { FoundHereChip(x, artistX, visible = true, touch = true, tabs = tabs, deck = deck) } }
    onNodeWithTag(FOUND_HERE_CHIP).performClick()

    onNodeWithText(sortTheseLabel(2)).performClick()
    waitForIdle()

    runOnIdle {
      assertTrue(deck.running)
      assertEquals(2, deck.total)
      assertEquals(find("Scenery"), deck.card)
      assertEquals(x, deck.goesOnTo)
    }
  }

  @Test
  fun noChipWhileHiddenOrWithoutFinds() = runComposeUiTest {
    setContent {
      SankakuTheme {
        FoundHereChip(x, artistX, visible = false, touch = true, tabs = tabs, deck = deck)
        FoundHereChip(find("Landscape"), landscape, visible = true, touch = true, tabs = tabs, deck = deck)
      }
    }

    onNodeWithTag(FOUND_HERE_CHIP).assertDoesNotExist()
  }

  @Test
  fun theCardsButtonsSortOrGoOn() = runComposeUiTest {
    runOnIdle { tabs.next() }
    setContent { SankakuTheme { BlockFindsCard(finds, privacy = null, tabs = tabs) } }

    onNodeWithText("LEAVING ARTIST X").assertIsDisplayed()
    onNodeWithText("2 found on Artist X").assertIsDisplayed()
    onNodeWithText("Scenery").assertIsDisplayed()
    onNodeWithText("$NOT_NOW · next: Landscape").performClick()

    runOnIdle {
      assertNull(finds.offer)
      assertEquals(find("Landscape"), tabs.activeTab)
    }
  }

  @Test
  fun sortThemNowStartsTheDeckGoingOnToTheNextItem() = runComposeUiTest {
    runOnIdle { tabs.next() }
    setContent { SankakuTheme { BlockFindsCard(finds, privacy = null, tabs = tabs) } }

    onNodeWithText(SORT_THEM_NOW).performClick()

    runOnIdle {
      assertNull(finds.offer)
      assertTrue(deck.running)
      assertEquals(find("Landscape"), deck.goesOnTo)
    }
  }

  @Test
  fun aFourthFindIsAndOneMore() = runComposeUiTest {
    runOnIdle {
      tabs.activate(x.id)
      navigator.openInNewTab(Route.Grid(listOf("sky")))
      navigator.openInNewTab(Route.Grid(listOf("cloud")))
      tabs.activate(tabs.tabs.value.last { it.foundOn != null }.id)
      tabs.activate(x.id)
      tabs.next()
    }
    setContent { SankakuTheme { BlockFindsCard(finds, privacy = null, tabs = tabs) } }

    onNodeWithText("4 found on Artist X").assertIsDisplayed()
    onNodeWithText("and 1 more").assertIsDisplayed()
  }

  @Test
  fun talkBackHearsAPaneWhoseHeadingHasBothAnswers() = runComposeUiTest {
    runOnIdle { tabs.next() }
    setContent { SankakuTheme { BlockFindsCard(finds, privacy = null, tabs = tabs) } }

    val pane = onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "2 found on Artist X"))
    pane.assertExists()
    val heading = onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
    val config = heading.fetchSemanticsNode().config
    assertTrue(config.contains(SemanticsProperties.Heading))
    val actions = config.getOrNull(SemanticsActions.CustomActions).orEmpty()
    assertEquals(listOf(SORT_THEM_NOW, "$NOT_NOW · next: Landscape"), actions.map { it.label })

    runOnIdle { actions.last().action() }
    runOnIdle { assertNull(finds.offer) }
  }
}
