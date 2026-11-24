package dev.marzahn.sankaku.features.post

import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoDragTest {
  private val player = FakeVideoPlayback(playing = true, position = 0.25f, hasMedia = true, firstFrameShown = true, duration = 120.0)
  private val keys = VideoKeys(PlayerPreferences(InMemoryTextFile())).also { it.register(player) }

  @Test
  fun aDragSeeksToKeyframesThenOnceExactlyAndPlaysOn() {
    keys.drag(0.5f)
    assertFalse(player.isPlaying, "the video holds while dragged")
    keys.drag(0.6f)
    assertEquals(0.6f, keys.draggedTo)

    keys.release()

    assertEquals(listOf(Seek(0.5f, dragging = true), Seek(0.6f, dragging = true), Seek(0.6f, dragging = false)), player.seeks)
    assertFalse(player.dragging)
    assertNull(keys.draggedTo)
    assertTrue(player.isPlaying, "it plays on, as it was playing")
  }

  @Test
  fun aPreviewedDragLeavesTheVideoPlayingUntilItLetsGo() {
    keys.previewsDrag = true
    keys.drag(0.5f)
    keys.drag(0.6f)
    assertTrue(player.isPlaying, "the video plays on while dragged")
    assertEquals(emptyList(), player.seeks)
    assertEquals(0.6f, keys.draggedTo)

    keys.release()

    assertEquals(listOf(Seek(0.6f, dragging = false)), player.seeks)
    assertNull(keys.draggedTo)
    assertTrue(player.isPlaying)
  }

  @Test
  fun aPausedVideoStaysPausedAfterADrag() {
    player.pause()

    keys.drag(0.9f)
    keys.release()

    assertFalse(player.isPlaying)
  }

  @Test
  fun aReleaseWithoutADragDoesNothing() {
    keys.release()

    assertTrue(player.seeks.isEmpty())
  }

  @Test
  fun theCacheKeyIgnoresTheSignature() {
    val post = dev.marzahn.sankaku.api.dto.PostDto(id = "1", md5 = "abc", rating = "s", status = "active")
    val a = videoCacheKey(post, videoSource("https://v.example.test/s/ab/abc.mp4?token=1"))
    val b = videoCacheKey(post, videoSource("https://v.example.test/s/ab/abc.mp4?token=2"))

    assertEquals("abc/abc.mp4", a)
    assertEquals(a, b)
  }
}
