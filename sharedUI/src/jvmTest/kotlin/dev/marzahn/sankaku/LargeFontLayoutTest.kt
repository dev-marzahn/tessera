package dev.marzahn.sankaku

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.tabs.UpNextCard
import dev.marzahn.sankaku.tabs.UpNextCardView
import dev.marzahn.sankaku.tabs.noUpNext
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.TagType
import dev.marzahn.sankaku.features.books.BookFilters
import dev.marzahn.sankaku.features.books.bookRowLayout
import dev.marzahn.sankaku.features.grid.Rating
import dev.marzahn.sankaku.features.grid.RatingBadge
import dev.marzahn.sankaku.features.grid.RatingBadgeSize
import dev.marzahn.sankaku.features.grid.RemovableSearchToken
import dev.marzahn.sankaku.features.grid.SearchSummary
import dev.marzahn.sankaku.features.grid.TitleRow
import dev.marzahn.sankaku.ui.components.TagBadge
import dev.marzahn.sankaku.ui.components.TagChip
import dev.marzahn.sankaku.ui.components.TagChipState
import dev.marzahn.sankaku.ui.components.MediaBadge
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LargeFontLayoutTest {
  private fun ComposeUiTest.show(fontScale: Float, content: @Composable () -> Unit) {
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(2f, fontScale = fontScale)) {
        SankakuTheme { Box(Modifier.width(PHONE)) { content() } }
      }
    }
    waitForIdle()
  }

  private fun ComposeUiTest.assertNoTextCut() {
    val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
      .fetchSemanticsNodes()
    assertTrue(texts.isNotEmpty())
    texts.forEach { node ->
      val results = mutableListOf<TextLayoutResult>()
      node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
      results.forEach {
        assertFalse(it.multiParagraph.height > it.size.height + 0.5f, "cut off: \"${it.layoutInput.text}\" in ${it.size}")
      }
    }
  }

  private fun ComposeUiTest.height(tag: String): Dp = onNodeWithTag(tag).getUnclippedBoundsInRoot().height

  @Test
  fun tagPillsAndRatingBadgesKeepTheirHeightAtNormalTextAndGrowAtTwice() {
    for ((scale, grows) in listOf(1f to false, 2f to true)) runComposeUiTest {
      show(scale) {
        Row {
          TagBadge("blue_hair", TagType.General, hint = null, modifier = Modifier.testTag("pill"))
          RatingBadge(Rating.G, Modifier.testTag("tag-rating"), size = RatingBadgeSize.Tag)
          RatingBadge(Rating.R15, Modifier.testTag("small-rating"))
          MediaBadge(icon = null, text = "01:05", description = "Video, 01:05", modifier = Modifier.testTag("video"))
        }
      }
      assertNoTextCut()
      if (!grows) {
        assertEquals(28f, height("pill").value, 0.5f)
        assertEquals(28f, height("tag-rating").value, 0.5f)
        assertEquals(20f, height("small-rating").value, 0.5f)
        assertEquals(24f, height("video").value, 0.5f)
      } else {
        assertTrue(height("pill") > 28.dp, "${height("pill")}")
        assertTrue(height("tag-rating") > 28.dp)
        assertTrue(height("small-rating") > 20.dp)
        assertTrue(height("video") > 24.dp)
      }
    }
  }

  @Test
  fun aTouchTagChipKeepsItsNameAndItsTarget() {
    for (scale in listOf(1f, 2f)) runComposeUiTest {
      show(scale) {
        TagChip(
          "blue_hair", TagType.General, count = "12k", state = TagChipState.Plain, selected = false,
          interactionSource = remember { MutableInteractionSource() }, modifier = Modifier.testTag("chip"), touch = true,
        )
      }
      assertNoTextCut()
      val chip = onNodeWithTag("chip").getUnclippedBoundsInRoot()
      if (scale == 1f) assertEquals(48f, chip.height.value, 0.5f) else assertTrue(chip.height >= 48.dp)
    }
  }

  @Test
  fun theSearchLineGrowsWithItsTags() = runComposeUiTest {
    show(2f) {
      SearchSummary(listOf("blue_hair"), setOf(Rating.G), known = emptyMap(), onOpen = {}, modifier = Modifier.testTag("line"))
    }
    assertNoTextCut()
    assertTrue(height("line") >= 56.dp)
  }

  @Test
  fun onTouchOnlyTheCrossRemovesASearchChip() {
    for (scale in listOf(1f, 2f)) for (tag in listOf("blue_hair", "sky")) runComposeUiTest {
      var removed = 0
      show(scale) { RemovableSearchToken(tag, known = emptyMap(), me = null, onRemove = { removed++ }, touch = true) }
      assertNoTextCut()

      val name = onNodeWithText(tag)
      name.performTouchInput { click(center) }
      name.performTouchInput { click(centerRight) }
      waitForIdle()
      assertEquals(0, removed, "a tap on \"$tag\" at $scale removed it")

      val cross = onNodeWithContentDescription("Remove $tag")
      val target = cross.getUnclippedBoundsInRoot()
      val bounds = name.getUnclippedBoundsInRoot()
      assertTrue(target.width >= 48.dp && target.height >= 48.dp, "$target")
      assertTrue(target.left >= bounds.right, "the target $target covers the name $bounds")
      cross.performClick()
      assertEquals(1, removed)
    }
  }

  @Test
  fun theViewerSheetsTitleAndBylineFitAtTwice() = runComposeUiTest {
    val post = PostDto(id = "1", md5 = "m", rating = "s", status = "active", author = PostDto.Author("uploader"))
    val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
    startKoin { modules(module { single { loader } }) }
    SingletonImageLoader.setUnsafe(loader)
    try {
      show(2f) { Box(Modifier.testTag("title")) { TitleRow(post, fullFraction = { 1f }, onThumbnail = {}) } }
      assertNoTextCut()
      assertTrue(height("title") > 44.dp)
    } finally {
      SingletonImageLoader.reset()
      stopKoin()
    }
  }

  @Test
  fun theBooksFiltersWrapAtTwice() = runComposeUiTest {
    show(2f) { BookFilters(listOf("order:popularity"), onChange = {}) }
    assertNoTextCut()
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes().forEach {
      assertTrue(it.boundsInRoot.right <= PHONE.value * 2 + 0.5f, "${it.boundsInRoot}")
    }
  }

  @Test
  fun aBookRowDropsPagesThenStacksBeforeTheTitleIsSqueezed() {
    val inside = 312.dp
    fun layout(heart: Boolean, scale: Float) =
      bookRowLayout(inside, cover = 72.dp, thumb = 48.dp, maxThumbs = 2, trailing = if (heart) 40.dp else 0.dp, fontScale = scale)
    for (heart in listOf(false, true)) {
      assertEquals(2, layout(heart, scale = 1f).thumbs)
      assertFalse(layout(heart, scale = 1f).stacked)
    }
    assertEquals(1, layout(heart = true, scale = 1.3f).thumbs)
    assertFalse(layout(heart = true, scale = 1.3f).stacked)
    assertTrue(layout(heart = true, scale = 2f).stacked)
    assertEquals(2, layout(heart = true, scale = 2f).thumbs)
  }

  @Test
  fun theEndOfTheQueueCardScrollsWhenTallerThanTheScreen() = runComposeUiTest {
    val tabs = TabsController()
    tabs.open(Route.Grid(listOf("scenery")), background = false)
    val state = noUpNext(tabs)
    state.forward()
    assertEquals(UpNextCard.End, state.card)
    show(2f) { Box(Modifier.size(PHONE, 300.dp)) { UpNextCardView(state = state) } }

    val skip = onNodeWithContentDescription("Browse, a new grid")
    assertTrue(skip.getUnclippedBoundsInRoot().bottom > 300.dp, "the card fits: nothing to show")
    skip.performScrollTo()
    waitForIdle()
    assertTrue(skip.getUnclippedBoundsInRoot().bottom <= 300.dp + 0.5.dp, "${skip.getUnclippedBoundsInRoot()}")
  }

  private companion object {
    val PHONE = 360.dp
  }
}
