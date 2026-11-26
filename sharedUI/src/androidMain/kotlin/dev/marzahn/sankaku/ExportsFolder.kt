package dev.marzahn.sankaku

import java.io.File

class ExportsFolder private constructor(val dir: File) {
  fun subfolder(name: String) = ExportsFolder(File(dir, name))

  companion object {
    private const val SHARED_CACHE_PATH = "exports"

    fun inCache(cacheDir: File) = ExportsFolder(File(cacheDir, SHARED_CACHE_PATH))
  }
}
