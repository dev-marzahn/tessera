package dev.marzahn.sankaku

import android.app.Application
import android.content.Context
import android.content.Intent
import dev.marzahn.sankaku.di.startSankakuKoin
import dev.marzahn.sankaku.log.CrashReports
import dev.marzahn.sankaku.log.installUncaughtHandler
import org.koin.android.ext.koin.androidContext
import org.koin.mp.KoinPlatform

class SankakuApplication : Application() {
  private lateinit var crashReports: CrashReports

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    crashReports = androidCrashReports(this).also { it.installUncaughtHandler() }
  }

  fun startKoinOnce(launch: Intent) {
    if (KoinPlatform.getKoinOrNull() != null) return
    startSankakuKoin(androidPlatformModule(launchDebugFlags(this, launch), crashReports)) {
      androidContext(this@SankakuApplication)
    }
  }
}
