package dev.marzahn.sankaku.features.firstlaunch

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.assert
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class QueueIntroTest {
  @Test
  fun thePageSaysTheFourPointsAndStartClosesIt() = runComposeUiTest {
    val intro = QueueIntro().apply { show() }
    setContent { SankakuTheme { QueueIntroScreen(onStart = intro::close) } }

    onNodeWithText(QueueIntroTitle).assert(isHeading())
    QueueIntroPoints.forEach { onNodeWithText(it).assertExists() }
    onAllNodesWithText("Kept post").assertCountEquals(0)

    assertTrue(intro.shown.value)
    onNodeWithTag(QueueIntroStartTag).performClick()
    assertFalse(intro.shown.value)
  }
}
