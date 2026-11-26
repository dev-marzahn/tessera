package dev.marzahn.sankaku.demo

import okio.FileSystem
import okio.Path

internal class MissingDemoMedia(name: String) : IllegalStateException("The demo media file $name is missing; run demo/generate-media.sh")

internal class DemoMedia(private val videos: Path) {
  fun find(name: String): ByteArray? = DemoMedia::class.java.getResourceAsStream("/demo/$name")?.use { it.readBytes() }

  @Synchronized
  fun playable(post: DemoPost): String {
    val file = videos / post.fileName
    if (!FileSystem.SYSTEM.exists(file)) {
      val bytes = find(post.fileName) ?: throw MissingDemoMedia(post.fileName)
      FileSystem.SYSTEM.createDirectories(videos)
      FileSystem.SYSTEM.write(file) { write(bytes) }
    }
    return file.toFile().toURI().toString()
  }
}
