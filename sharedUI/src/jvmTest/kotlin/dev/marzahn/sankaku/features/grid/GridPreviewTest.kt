package dev.marzahn.sankaku.features.grid

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.marzahn.sankaku.api.dto.UserSummary
import dev.marzahn.sankaku.auth.StoredTokens
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.shell.GridRefresh
import dev.marzahn.sankaku.tabs.SourcePreview
import dev.marzahn.sankaku.tabs.SourceSize
import dev.marzahn.sankaku.tabs.isNewSource
import org.koin.core.context.GlobalContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalTestApi::class)
class GridPreviewTest {
  private fun run(fixture: GridScreenFixture, test: ComposeUiTest.(GridScreenFixture) -> Unit) {
    fixture.start()
    try {
      runDesktopComposeUiTest(width = 1280, height = 832) {
        with(fixture) { showGrid() }
        test(fixture)
      }
    } finally {
      fixture.stop()
    }
  }

  private fun GridScreenFixture.preview(): SourcePreview? = tabs.activeTab.preview

  @Test
  fun theGridsFirstPageIsTheSourcesPreview() = run(GridScreenFixture()) { fixture ->
    waitUntil(timeoutMillis = 5_000) { fixture.preview() != null }

    val preview = fixture.preview()!!
    assertEquals((1..6).map { "fixture-md5-$it" }, preview.thumbs.map { it.md5 })
    assertTrue(preview.g)
    assertEquals(SourceSize.Counted(6, more = false), preview.size)
    assertTrue(isNewSource(fixture.tabs.activeTab))
  }

  @Test
  fun eachPageTheGridLoadsCountsTowardsItsSize() = run(GridScreenFixture(secondPageAtStart = GridScreenFixture.SecondPage.Hold)) { fixture ->
    waitUntil(timeoutMillis = 5_000) { fixture.preview() != null }
    assertEquals(SourceSize.Counted(6, more = true), fixture.preview()!!.size)

    fixture.releaseSecondPage()
    waitUntil(timeoutMillis = 5_000) { fixture.preview()?.size == SourceSize.Counted(8, more = false) }
    assertEquals((1..8).map { "fixture-md5-$it" }, fixture.preview()!!.thumbs.map { it.md5 })
  }

  @Test
  fun aGOnlyChangeAfterARefreshStillRefreshesThePreview() = run(GridScreenFixture(tokens = signedIn())) { fixture ->
    waitUntil(timeoutMillis = 5_000) { fixture.preview() != null }
    assertFalse(fixture.preview()!!.g)

    val refresh = GlobalContext.get().get<GridRefresh>()
    runOnIdle { refresh.request() }
    waitUntil(timeoutMillis = 5_000) { !refresh.running.value }
    GlobalContext.get().get<PrivacySettings>().setGOnly(true)

    waitUntil(timeoutMillis = 5_000) { fixture.preview()?.g == true }
  }

  private fun signedIn(): StoredTokens {
    val at = Clock.System.now()
    return StoredTokens("access", "refresh", at + 7.days, at + 90.days, UserSummary(id = "42", name = "tester", avatarUrl = null))
  }
}
