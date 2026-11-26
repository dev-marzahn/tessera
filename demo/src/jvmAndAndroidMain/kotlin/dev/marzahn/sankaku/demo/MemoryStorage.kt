package dev.marzahn.sankaku.demo

import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.TextFile
import dev.marzahn.sankaku.data.TextFiles
import dev.marzahn.sankaku.features.post.SaveFolder
import dev.marzahn.sankaku.features.post.SavedFile
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import java.util.concurrent.ConcurrentHashMap

internal class MemoryTextFiles : TextFiles {
  private val files = ConcurrentHashMap<String, TextFile>()

  override fun named(name: String): TextFile = files.computeIfAbsent(name) { InMemoryTextFile() }
}

internal class MemorySaveFolder : SaveFolder {
  private val saved = ConcurrentHashMap.newKeySet<String>()

  override suspend fun find(name: String): SavedFile? = if (name in saved) NothingToOpen else null

  override suspend fun save(name: String, mimeType: String?, write: suspend (RawSink) -> Unit): SavedFile {
    write(Buffer())
    saved += name
    return NothingToOpen
  }

  private object NothingToOpen : SavedFile {
    override fun open() = Unit
  }
}
