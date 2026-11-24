package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class VideoRotationTest {
  private val post = PostDto(
    id = "1", md5 = "abc", rating = "g", status = "active", fileType = "video/mp4",
    sampleUrl = "https://v.example.test/data/sample/ab/sample-abc.mp4?token=t",
  )

  private class Player : VideoPlayback {
    var openedPlaying: Boolean? = null
    val seeks = mutableListOf<Float>()
    override var playWhenReady by mutableStateOf(false)
    override val isPlaying get() = playWhenReady

    var inBackground = false
    override val playsOnReturn get() = playWhenReady || inBackground
    override val hasMedia = false
    override val failed = false
    override val firstFrameShown = true
    override var duration by mutableStateOf(0.0)
    override var position by mutableStateOf(0f)
    override var volume = 1f
    override var dragging = false

    override fun open(url: String, cacheKey: String, play: Boolean) {
      openedPlaying = play
      playWhenReady = play
      position = 0f
      duration = 100.0
    }

    override fun play() {
      playWhenReady = true
    }

    override fun pause() {
      playWhenReady = false
    }

    override fun seekTo(fraction: Float) {
      seeks += fraction
      position = fraction
    }

    override fun release() {}
  }

  private var registry by mutableStateOf(SaveableStateRegistry(null) { true })
  private var player by mutableStateOf(Player())

  private fun rotate(): Player {
    val saved = registry.performSave()
    registry = SaveableStateRegistry(saved) { true }
    player = Player()
    return player
  }

  private fun androidx.compose.ui.test.ComposeUiTest.start() {
    setContent {
      key(registry) {
        CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
          val onScreen = remember { Any() }
          key(onScreen) {
            val video = remember { VideoKeys(PlayerPreferences(InMemoryTextFile())) }
            rememberSaveable(video, key = VIEWER_VIDEO_KEY, saver = remember(video) { videoPlaceSaver(video) }) { video }
            CompositionLocalProvider(LocalVideoKeys provides video) {
              key(remember { Any() }) { PostVideo(post, player = player, preferences = PlayerPreferences(InMemoryTextFile())) }
            }
          }
        }
      }
    }
    waitForIdle()
  }

  @Test
  fun anotherPostsVideoDropsTheRestoredPlace() {
    val keys = VideoKeys(PlayerPreferences(InMemoryTextFile()))
    keys.restored = VideoPlace("1", at = 0.4f, paused = true)

    keys.register(Player(), postId = "2")

    assertEquals(null, keys.restoredPlace("1"))
  }

  @Test
  fun aVideoShownAgainAfterItWentStartsAfresh() = runComposeUiTest {
    var shown by mutableStateOf(true)
    setContent {
      key(registry) {
        CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
          val video = remember { VideoKeys(PlayerPreferences(InMemoryTextFile())) }
          rememberSaveable(video, key = VIEWER_VIDEO_KEY, saver = remember(video) { videoPlaceSaver(video) }) { video }
          CompositionLocalProvider(LocalVideoKeys provides video) {
            if (shown) PostVideo(post, player = player, preferences = PlayerPreferences(InMemoryTextFile()))
          }
        }
      }
    }
    waitForIdle()
    runOnIdle {
      player.position = 0.4f
      player.pause()
    }
    runOnIdle { rotate() }
    waitForIdle()

    runOnIdle { shown = false }
    runOnIdle {
      player = Player()
      shown = true
    }
    waitForIdle()

    assertEquals(true, player.openedPlaying)
    assertEquals(emptyList(), player.seeks)
  }

  @Test
  fun aPlayingVideoPlaysOnFromWhereItWas() = runComposeUiTest {
    start()
    runOnIdle { player.position = 0.4f }

    val after = runOnIdle { rotate() }
    waitForIdle()

    assertEquals(true, after.openedPlaying)
    assertEquals(listOf(0.4f), after.seeks)
    assertEquals(true, after.playWhenReady)
  }

  @Test
  fun aPausedVideoStaysPausedWhereItWas() = runComposeUiTest {
    start()
    runOnIdle {
      player.position = 0.4f
      player.pause()
    }

    val after = runOnIdle { rotate() }
    waitForIdle()

    assertEquals(false, after.openedPlaying)
    assertEquals(listOf(0.4f), after.seeks)
    assertEquals(false, after.playWhenReady)
  }

  @Test
  fun aVideoPausedOnlyByTheRotationsStopPlaysOn() = runComposeUiTest {
    start()
    runOnIdle {
      player.position = 0.4f
      player.pause()
      player.inBackground = true
    }

    val after = runOnIdle { rotate() }
    waitForIdle()

    assertEquals(true, after.openedPlaying)
    assertEquals(true, after.playWhenReady)
  }

  @Test
  fun aSecondRotationBeforeItLoadedKeepsThePlace() = runComposeUiTest {
    start()
    runOnIdle {
      player.position = 0.4f
      player.pause()
    }
    runOnIdle { rotate() }
    waitForIdle()
    runOnIdle { player.duration = 0.0 }

    val after = runOnIdle { rotate() }
    waitForIdle()

    assertEquals(false, after.openedPlaying)
    assertEquals(listOf(0.4f), after.seeks)
  }
}
