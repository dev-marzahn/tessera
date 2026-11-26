package dev.marzahn.sankaku.features.post

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
class VideoCacheStorage(val folder: File, val database: DatabaseProvider) {
  fun deleteLegacyCache(context: Context) {
    val legacy = File(context.cacheDir, LEGACY_FOLDER)
    if (legacy.exists()) Thread { runCatching { SimpleCache.delete(legacy, database) } }.start()
  }

  companion object {
    private const val LEGACY_FOLDER = "compose_media_player_cache"

    fun appDatabase(context: Context): DatabaseProvider = StandaloneDatabaseProvider(context.applicationContext)
  }
}
