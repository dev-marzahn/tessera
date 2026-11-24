package dev.marzahn.sankaku.data

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files

object AppDataMigration {
  private const val LOCK_FILE = "instance.lock"

  fun run(from: File, to: File) {
    if (!from.isDirectory) return
    val lockFile = File(from, LOCK_FILE)
    val moved = try {
      RandomAccessFile(lockFile, "rw").use { file ->
        val lock = try {
          file.channel.tryLock()
        } catch (_: OverlappingFileLockException) {
          null
        } ?: return
        lock.use { moveEntries(from, to) }
      }
      true
    } catch (_: IOException) {
      false
    }
    if (!moved) return
    lockFile.delete()
    from.delete()
  }

  private fun moveEntries(from: File, to: File) {
    val entries = from.listFiles() ?: return
    to.mkdirs()
    for (entry in entries) {
      if (entry.name == LOCK_FILE) continue
      val target = File(to, entry.name)
      if (target.exists()) continue
      try {
        Files.move(entry.toPath(), target.toPath())
      } catch (_: IOException) {
      }
    }
  }
}
