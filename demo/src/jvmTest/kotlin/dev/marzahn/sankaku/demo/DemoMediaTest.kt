package dev.marzahn.sankaku.demo

import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant

class DemoMediaTest {
  private val scratch = Files.createTempDirectory("demo-media").toOkioPath()
  private val videos = scratch / "media"
  private val media = DemoMedia(videos)

  private val missing = DemoPost(
    media = "not-bundled",
    fileType = "video/mp4",
    extension = "mp4",
    width = 1,
    height = 1,
    tags = emptyList(),
    postedAt = Instant.DISTANT_PAST,
    videoSeconds = 1.0,
  )

  @AfterTest
  fun deleteScratch() = FileSystem.SYSTEM.deleteRecursively(scratch)

  @Test
  fun aMissingVideoNamesItsFileAndLeavesNoEmptyFileBehind() {
    repeat(2) {
      val failure = assertFailsWith<MissingDemoMedia> { media.playable(missing) }
      assertContains(failure.message.orEmpty(), "not-bundled.mp4")
    }
    assertFalse(FileSystem.SYSTEM.exists(videos / missing.fileName))
  }

  @Test
  fun aBundledVideoIsCopiedWhole() {
    val video = DemoCatalogue.posts.first { it.isVideo }
    media.playable(video)
    assertEquals(media.find(video.fileName)!!.size.toLong(), FileSystem.SYSTEM.metadata(videos / video.fileName).size)
  }
}
