package dev.marzahn.sankaku

import android.app.Application
import android.content.Context
import android.content.Intent
import dev.marzahn.sankaku.di.startSankakuKoin
import dev.marzahn.sankaku.log.installUncaughtHandler
import org.koin.android.ext.koin.androidContext
import org.koin.mp.KoinPlatform

class SankakuApplication : Application() {
  private lateinit var installed: AndroidLaunch.Installed
  private lateinit var crashes: LaunchCrashes

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    installed = AndroidLaunch.Installed(androidCrashReports(this))
    crashes = LaunchCrashes(installed.crashReports)
    installUncaughtHandler { crashes.current }
  }

  fun startKoinOnce(intent: Intent) {
    if (KoinPlatform.getKoinOrNull() != null) return
    val launch = launchChoice.launch(this, intent, installed)
    crashes.follow(launch)
    startSankakuKoin(androidPlatformModule(launchDebugFlags(this, intent), launch.crashReports), launch.overrides) {
      androidContext(this@SankakuApplication)
    }
  }
}
