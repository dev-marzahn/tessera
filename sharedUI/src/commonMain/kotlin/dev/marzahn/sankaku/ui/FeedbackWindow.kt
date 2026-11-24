package dev.marzahn.sankaku.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.awaitCancellation

const val LONG_WINDOW_MS = 10_000L

const val SHORT_WINDOW_MS = 4_000L

@Composable
fun rememberActionWindowMillis(): Long = rememberRecommendedMillis(LONG_WINDOW_MS)

@Composable
fun rememberConfirmWindowMillis(): Long = rememberRecommendedMillis(SHORT_WINDOW_MS)

@Composable
private fun rememberRecommendedMillis(base: Long): Long {
  val manager = LocalAccessibilityManager.current
  return manager?.calculateRecommendedTimeoutMillis(base, containsIcons = true, containsText = true, containsControls = true)
    ?: base
}

suspend fun runWindow(totalMillis: Long, isPaused: () -> Boolean = { false }, onLeft: (Float) -> Unit) {
  onLeft(1f)
  if (totalMillis == Long.MAX_VALUE) awaitCancellation()
  var used = 0L
  var last = withFrameMillis { it }
  while (used < totalMillis) {
    val now = withFrameMillis { it }
    if (!isPaused()) used += now - last
    last = now
    onLeft((1f - used.toFloat() / totalMillis).coerceIn(0f, 1f))
  }
}
