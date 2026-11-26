package dev.marzahn.sankaku

import android.content.Context
import android.content.Intent
import dev.marzahn.sankaku.log.CrashReports
import org.koin.core.module.Module

sealed interface AndroidLaunch {
  val crashReports: CrashReports
  val overrides: List<Module>

  class Installed(override val crashReports: CrashReports) : AndroidLaunch {
    override val overrides = emptyList<Module>()
  }
}

fun interface LaunchChoice {
  fun launch(context: Context, intent: Intent, installed: AndroidLaunch.Installed): AndroidLaunch
}

class LaunchCrashes(private val installed: CrashReports) {
  @Volatile
  private var launched: CrashReports? = null

  val current: CrashReports get() = launched ?: installed

  fun follow(launch: AndroidLaunch) {
    launched = launch.crashReports
  }
}
