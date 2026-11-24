package dev.marzahn.sankaku.data

import java.io.File

class DesktopFolders(private val home: File) {
  val appData: File get() = File(home, "Library/Application Support/Tessera")
  val cache: File get() = File(home, "Library/Caches/Tessera")

  val downloads: File get() = File(home, "Downloads/Tessera")

  val oldAppData: File get() = File(home, "Library/Application Support/Sankaku")
}
