package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal class FakeVideoPlayers {
  val all = mutableListOf<FakeVideoPlayback>()

  val live: List<FakeVideoPlayback> get() = all.filterNot { it.released }

  val playback: @Composable () -> VideoPlayback = { remember { FakeVideoPlayback().also { all += it } } }
}
