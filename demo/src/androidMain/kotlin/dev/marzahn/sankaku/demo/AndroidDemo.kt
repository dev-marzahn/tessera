package dev.marzahn.sankaku.demo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DefaultDatabaseProvider
import dev.marzahn.sankaku.ExportsFolder
import dev.marzahn.sankaku.features.post.VideoCacheStorage
import okio.Path.Companion.toOkioPath
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

fun androidDemoModules(context: Context): List<Module> {
  val scratch = File(context.cacheDir, "demo")
  val exports = ExportsFolder.inCache(context.cacheDir).subfolder("demo")
  empty(scratch)
  empty(exports.dir)
  return listOf(demoModule(scratch.toOkioPath()), androidStorageModule(context, scratch, exports))
}

private fun empty(folder: File) {
  val earlier = File(folder.parentFile, "${folder.name}-earlier-${System.nanoTime()}")
  if (folder.renameTo(earlier)) Thread { earlier.deleteRecursively() }.start()
  folder.mkdirs()
}

@OptIn(UnstableApi::class)
private fun androidStorageModule(context: Context, scratch: File, exports: ExportsFolder): Module = module {
  single { VideoCacheStorage(scratch.resolve("video"), DefaultDatabaseProvider(ScratchDatabase(context, scratch.resolve("video.db")))) }
  single { exports }
}

private class ScratchDatabase(context: Context, file: File) : SQLiteOpenHelper(context, file.path, null, 1) {
  override fun onCreate(db: SQLiteDatabase) = Unit

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
