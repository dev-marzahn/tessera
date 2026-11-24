package dev.marzahn.sankaku.images

import coil3.disk.DiskCache
import okio.FileSystem
import okio.Path

class ResizableDiskCache(maxSize: Long, private val build: (maxSize: Long) -> DiskCache) : DiskCache {
  private val lock = Any()
  private var current = build(maxSize)
  private var open = 0
  private var pending: Long? = null

  override val size: Long get() = synchronized(lock) { current }.size

  override val maxSize: Long get() = synchronized(lock) { pending ?: current.maxSize }

  override val directory: Path get() = synchronized(lock) { current }.directory

  override val fileSystem: FileSystem get() = synchronized(lock) { current }.fileSystem

  override fun openSnapshot(key: String): DiskCache.Snapshot? = synchronized(lock) {
    current.openSnapshot(key)?.let(::counted)
  }

  override fun openEditor(key: String): DiskCache.Editor? = synchronized(lock) {
    current.openEditor(key)?.let(::counted)
  }

  override fun remove(key: String): Boolean = synchronized(lock) { current.remove(key) }

  override fun clear() = synchronized(lock) { current.clear() }

  override fun shutdown() = synchronized(lock) { current.shutdown() }

  fun resize(maxSize: Long) {
    val swapped = synchronized(lock) {
      pending = maxSize
      swapIfIdle()
    }
    swapped?.let(::trim)
  }

  private fun swapIfIdle(): DiskCache? {
    val size = pending ?: return null
    if (open > 0) return null
    pending = null
    if (size == current.maxSize) return null
    val next = runCatching { build(size) }.getOrNull() ?: return null
    current.shutdown()
    current = next
    return next
  }

  private fun trim(cache: DiskCache) {
    runCatching {
      cache.openEditor(TRIM_KEY)?.let { editor ->
        runCatching {
          cache.fileSystem.write(editor.metadata) {}
          cache.fileSystem.write(editor.data) {}
          editor.commit()
        }.onFailure { editor.abort() }
      }
      cache.remove(TRIM_KEY)
    }
  }

  private fun release() {
    val swapped = synchronized(lock) {
      open--
      swapIfIdle()
    }
    swapped?.let(::trim)
  }

  private fun counted(snapshot: DiskCache.Snapshot): DiskCache.Snapshot {
    open++
    return CountedSnapshot(snapshot)
  }

  private fun counted(editor: DiskCache.Editor): DiskCache.Editor {
    open++
    return CountedEditor(editor)
  }

  private inner class CountedSnapshot(private val delegate: DiskCache.Snapshot) : DiskCache.Snapshot {
    private var done = false
    override val metadata: Path get() = delegate.metadata
    override val data: Path get() = delegate.data

    override fun close() {
      try {
        delegate.close()
      } finally {
        finish()
      }
    }

    override fun closeAndOpenEditor(): DiskCache.Editor? = try {
      synchronized(lock) { delegate.closeAndOpenEditor()?.let(::counted) }
    } finally {
      finish()
    }

    private fun finish() {
      if (synchronized(lock) { done.also { done = true } }) return
      release()
    }
  }

  private inner class CountedEditor(private val delegate: DiskCache.Editor) : DiskCache.Editor {
    private var done = false
    override val metadata: Path get() = delegate.metadata
    override val data: Path get() = delegate.data

    override fun commit() {
      try {
        delegate.commit()
      } finally {
        finish()
      }
    }

    override fun commitAndOpenSnapshot(): DiskCache.Snapshot? = try {
      synchronized(lock) { delegate.commitAndOpenSnapshot()?.let(::counted) }
    } finally {
      finish()
    }

    override fun abort() {
      try {
        delegate.abort()
      } finally {
        finish()
      }
    }

    private fun finish() {
      if (synchronized(lock) { done.also { done = true } }) return
      release()
    }
  }

  private companion object {
    const val TRIM_KEY = "dev.marzahn.sankaku.resize"
  }
}
