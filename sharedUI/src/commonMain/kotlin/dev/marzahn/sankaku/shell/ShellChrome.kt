package dev.marzahn.sankaku.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

val LocalCoverChrome = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
fun CoverChrome(enabled: Boolean) {
  val report = LocalCoverChrome.current
  SideEffect { report(enabled) }
}

val LocalChromePadding = compositionLocalOf { PaddingValues() }

val LocalTabBarSlide = staticCompositionLocalOf<() -> Float> { { 0f } }
