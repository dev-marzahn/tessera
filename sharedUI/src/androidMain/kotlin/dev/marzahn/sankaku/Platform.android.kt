package dev.marzahn.sankaku

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import android.view.Window
import android.view.WindowManager
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.coroutines.cancellation.CancellationException
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dev.marzahn.sankaku.auth.TinkTokenStore
import dev.marzahn.sankaku.auth.TokenStorageStatus
import dev.marzahn.sankaku.auth.TokenStore
import dev.marzahn.sankaku.data.DiskTextFile
import dev.marzahn.sankaku.data.PostSizes
import dev.marzahn.sankaku.data.TextFile
import dev.marzahn.sankaku.log.AppEnvironment
import dev.marzahn.sankaku.log.CrashReports
import dev.marzahn.sankaku.features.post.MediaStoreSaveFolder
import dev.marzahn.sankaku.features.post.SaveFolder
import dev.marzahn.sankaku.net.DefaultNetworkCallback
import dev.marzahn.sankaku.tabs.FileTabStateStore
import dev.marzahn.sankaku.tabs.TabStateStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import okio.Path.Companion.toOkioPath
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

fun launchDebugFlags(context: Context, intent: Intent): DebugFlags =
  if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
    DebugFlags()
  } else {
    DebugFlags(
      expireAccessToken = intent.getBooleanExtra("expireAccessToken", false),
      expireSession = intent.getBooleanExtra("expireSession", false),
      safeOnly = safeOnlyRequested(intent.getBooleanExtra("safeOnly", false), systemProperty(SAFE_ONLY_PROPERTY)),
      crash = DebugCrash.parse(intent.getStringExtra("crash")),
      openBook = intent.getStringExtra("openBook")?.trim()?.takeIf { it.isNotEmpty() },
    )
  }

private fun systemProperty(name: String): String? = runCatching {
  val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
  try {
    if (!process.waitFor(1, TimeUnit.SECONDS)) return@runCatching null
    process.inputStream.bufferedReader().use { it.readText() }
  } finally {
    process.destroy()
  }
}.getOrNull()

fun androidCrashReports(context: Context): CrashReports =
  CrashReports(DiskTextFile(java.io.File(context.filesDir, "crash.txt")))

fun androidPlatformModule(flags: DebugFlags, crashReports: CrashReports): Module = module {
  single<TokenStore> { TinkTokenStore.create(androidContext()) }
  single { TokenStorageStatus.Encrypted }
  single { flags }
  single { crashReports }
  single(CACHE_DIR) { androidContext().cacheDir.toOkioPath() }
  single<TextFile>(KNOWN_TAGS_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "tags.json")) }
  single<TextFile>(PLAYER_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "player.json")) }
  single<TextFile>(REOPEN_HINT_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "reopen-hint.json")) }
  single<TextFile>(PRIVACY_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "privacy.json")) }
  single<TextFile>(FIRST_LAUNCH_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "first-launch.json")) }
  single<TextFile>(ACCESSIBILITY_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "accessibility.json")) }
  single<TextFile>(QUEUE_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "queue.json")) }
  single<TextFile>(GRID_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "grid.json")) }
  single<TextFile>(CACHE_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "cache.json")) }
  single<TextFile>(UPDATES_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "updates.json")) }
  single<TextFile>(TIPS_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "tips.json")) }
  single<TextFile>(SEEN_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "seen.json")) }
  single<TextFile>(FEED_MARKS_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "feed-marks.json")) }
  single<TextFile>(TODAY_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "today.json")) }
  single<TextFile>(READING_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "readings.json")) }
  single<TextFile>(OUTBOX_FILE) { DiskTextFile(java.io.File(androidContext().filesDir, "outbox.json")) }
  single<TabStateStore> {
    val files = androidContext().filesDir
    FileTabStateStore(
      DiskTextFile(java.io.File(files, "tabs.json")),
      backup = DiskTextFile(java.io.File(files, "tabs.v1.bak")),
      broken = DiskTextFile(java.io.File(files, "tabs.json.broken")),
    )
  }
  single<SaveFolder> { MediaStoreSaveFolder(androidContext()) }
  single(createdAtStart = true) { DefaultNetworkCallback.register(androidContext(), get()) }
}

actual val platformHasTabs: Boolean = true

@Composable
actual fun platformUses24HourClock(): Boolean = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)

actual fun platformOpenUrl(url: String): OpenUrlResult {
  val context = KoinPlatform.getKoin().get<Context>()
  return launchUrl(url, context::startActivity)
}

internal fun launchUrl(url: String, start: (Intent) -> Unit): OpenUrlResult = try {
  val intent = Intent(Intent.ACTION_VIEW, url.toUri())
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  start(intent)
  OpenUrlResult.Opened
} catch (_: ActivityNotFoundException) {
  OpenUrlResult.Failed
} catch (_: SecurityException) {
  OpenUrlResult.Failed
}

actual fun platformShareText(text: String) {
  val context = KoinPlatform.getKoin().get<Context>()
  val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
  context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

actual fun platformShareFile(fileName: String, mimeType: String, text: String) {
  val context = KoinPlatform.getKoin().get<Context>()
  val file = java.io.File(exportsDir(context).apply { mkdirs() }, fileName)
  file.writeText(text)
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
  val send = Intent(Intent.ACTION_SEND)
    .setType(mimeType)
    .putExtra(Intent.EXTRA_STREAM, uri)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  send.clipData = ClipData.newRawUri(fileName, uri)
  context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

actual fun platformClearExports() {
  exportsDir(KoinPlatform.getKoin().get<Context>()).deleteRecursively()
}

private fun exportsDir(context: Context) = java.io.File(context.cacheDir, "exports")

actual fun platformAppDataText(name: String): String? {
  val context = KoinPlatform.getKoin().get<Context>()
  return runCatching { java.io.File(context.filesDir, name).readText() }.getOrNull()
}

actual fun plainTextClip(text: String): ClipEntry = ClipEntry(ClipData.newPlainText(text, text))

actual fun processMemoryBytes(): Long? = Debug.getPss().takeIf { it > 0 }?.let { it * 1024 }

actual val processMemoryTargetBytes: Long = 512L * 1024 * 1024

actual fun Modifier.platformBackKeys(onBack: () -> Unit): Modifier = this

actual fun Modifier.platformGestureExclusion(): Modifier = systemGestureExclusion()

@Composable
actual fun GridScrollbar(state: LazyStaggeredGridState, postCount: Int, modifier: Modifier) = Unit

actual val gridScrollbarRoom: Dp = 0.dp

actual val platformIsTouch: Boolean = true

actual val platformPostSizes: PostSizes = PostSizes.Phone

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled = enabled, onBack = onBack)

actual val platformCellGap: Dp = 4.dp

actual val platformGridEdge: Dp = 6.dp

@Composable
actual fun PlatformSystemBars(hidden: Boolean, lightIcons: Boolean) {
  val window = LocalView.current.context.findActivity()?.window ?: return
  val requests = remember(window) { window.barRequests() }
  val appLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
  SystemBarRequest(requests, SystemBarsLook(hidden = hidden, lightIcons = lightIcons, appLight = appLight))
}

private data class SystemBarsLook(val hidden: Boolean, val lightIcons: Boolean, val appLight: Boolean)

private val windowBarRequests = WeakHashMap<Window, SystemBarRequests<SystemBarsLook>>()

private fun Window.barRequests(): SystemBarRequests<SystemBarsLook> = windowBarRequests.getOrPut(this) {
  val window = WeakReference(this)
  fun controller() = window.get()?.let { WindowCompat.getInsetsController(it, it.decorView) }
  val bars = WindowInsetsCompat.Type.systemBars()
  lateinit var requests: SystemBarRequests<SystemBarsLook>
  requests = SystemBarRequests(
    apply = { look ->
      controller()?.run {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (look.hidden) hide(bars) else show(bars)
        isAppearanceLightStatusBars = !look.lightIcons
      }
    },
    restore = { look ->
      window.get()?.decorView?.post {
        if (requests.isEmpty) {
          controller()?.run {
            show(bars)
            isAppearanceLightStatusBars = look.appLight
          }
        }
      }
    },
  )
  requests
}

@Composable
actual fun PlatformHideInRecents(hide: Boolean) {
  val activity = LocalView.current.context.findActivity() ?: return
  DisposableEffect(activity, hide) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      activity.setRecentsScreenshotEnabled(!hide)
    } else if (hide) {
      activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
      activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    onDispose {}
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

@Composable
actual fun PlatformPredictiveBackHandler(enabled: Boolean, onProgress: (Float) -> Unit, onCancel: () -> Unit, onBack: () -> Unit) {
  val progress by rememberUpdatedState(onProgress)
  val cancel by rememberUpdatedState(onCancel)
  val back by rememberUpdatedState(onBack)
  PredictiveBackHandler(enabled = enabled) { events ->
    try {
      events.collect { progress(it.progress) }
      back()
    } catch (e: CancellationException) {
      cancel()
      throw e
    }
  }
}

actual fun platformEnvironment(): AppEnvironment = AppEnvironment(
  version = APP_VERSION,
  platform = "Android",
  os = "Android ${Build.VERSION.RELEASE}",
  device = androidDevice(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty()),
)

internal fun androidDevice(manufacturer: String, model: String): String = when {
  manufacturer.isBlank() || model.startsWith(manufacturer, ignoreCase = true) -> model
  else -> "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
}
