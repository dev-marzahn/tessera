package dev.marzahn.sankaku

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import dev.marzahn.sankaku.ui.components.Shortcuts
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import com.github.javakeyring.Keyring
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.auth.KeyringTokenStore
import dev.marzahn.sankaku.auth.TokenStorageStatus
import dev.marzahn.sankaku.auth.TokenStore
import dev.marzahn.sankaku.data.AppDataMigration
import dev.marzahn.sankaku.data.DesktopFolders
import dev.marzahn.sankaku.data.DiskTextFile
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PostSizes
import dev.marzahn.sankaku.data.TextFile
import dev.marzahn.sankaku.data.TextFiles
import dev.marzahn.sankaku.di.storedFilesModule
import dev.marzahn.sankaku.features.post.DirectorySaveFolder
import dev.marzahn.sankaku.features.post.SaveFolder
import dev.marzahn.sankaku.log.AppEnvironment
import dev.marzahn.sankaku.log.CrashReports
import dev.marzahn.sankaku.net.InterfaceWatcher
import org.koin.core.module.Module
import okio.Path.Companion.toOkioPath
import org.koin.dsl.module
import java.awt.Desktop
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

private val folders = DesktopFolders(File(System.getProperty("user.home")))
private val dataDirOverride = System.getProperty("sankaku.debug.dataDir")?.let(::File)
val APP_DATA_DIR = dataDirOverride ?: folders.appData

fun migrateAppData() {
  val old = if (dataDirOverride == null) {
    folders.oldAppData
  } else {
    System.getProperty("sankaku.debug.oldDataDir")?.let(::File) ?: return
  }
  AppDataMigration.run(old, APP_DATA_DIR)
}

fun desktopCrashReports(): CrashReports = CrashReports(DiskTextFile(File(APP_DATA_DIR, "crash.txt")))

internal fun tokenKeyring(ownDataDir: Boolean, create: () -> Keyring): Keyring? =
  if (ownDataDir) null else runCatching(create).getOrNull()

private fun ownersKeyring(): Keyring? = tokenKeyring(ownDataDir = dataDirOverride != null) { Keyring.create() }

private val isPackaged = System.getProperty("jpackage.app-path") != null

fun desktopPlatformModule(crashReports: CrashReports, openKeyring: () -> Keyring? = ::ownersKeyring): Module = module {
  includes(storedFilesModule)
  val keyringWhenATokenIsNeeded by lazy(openKeyring)
  single<TokenStore> { keyringWhenATokenIsNeeded?.let(::KeyringTokenStore) ?: InMemoryTokenStore() }
  single { if (keyringWhenATokenIsNeeded != null) TokenStorageStatus.Keychain else TokenStorageStatus.InMemory }
  single {
    DebugFlags(
      sample = System.getProperty("sankaku.sample").toBoolean(),
      expireAccessToken = System.getProperty("sankaku.debug.expireAccessToken").toBoolean(),
      expireSession = System.getProperty("sankaku.debug.expireSession").toBoolean(),
      safeOnly = System.getProperty("sankaku.debug.safeOnly").toBoolean(),
      crash = if (isPackaged) null else DebugCrash.parse(System.getProperty("sankaku.debug.crash")),
      openBook = if (isPackaged) null else System.getProperty("sankaku.debug.openBook")?.trim()?.takeIf { it.isNotEmpty() },
    )
  }
  single { crashReports }
  single(CACHE_DIR) { folders.cache.toOkioPath() }
  single { TextFiles { name -> DiskTextFile(File(APP_DATA_DIR, name)) } }
  single<TextFile>(ACCESSIBILITY_FILE) { InMemoryTextFile() }
  single<SaveFolder> { DirectorySaveFolder(folders.downloads) }
  single<PlatformContext> { PlatformContext.INSTANCE }
  single(createdAtStart = true) { InterfaceWatcher(get(), get()).also { it.start() } }
}

actual val platformHasTabs: Boolean = true

@Composable
actual fun platformUses24HourClock(): Boolean = androidx.compose.runtime.remember {
  (java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) as? java.text.SimpleDateFormat)?.toPattern()?.contains('H') ?: true
}

actual fun platformOpenUrl(url: String): OpenUrlResult = launchUrl(url) { Desktop.getDesktop().browse(it) }

internal fun launchUrl(url: String, browse: (URI) -> Unit): OpenUrlResult = try {
  browse(URI(url))
  OpenUrlResult.Opened
} catch (_: UnsupportedOperationException) {
  OpenUrlResult.Failed
} catch (_: IOException) {
  OpenUrlResult.Failed
} catch (_: SecurityException) {
  OpenUrlResult.Failed
} catch (_: URISyntaxException) {
  OpenUrlResult.Failed
}

actual fun platformShareText(text: String) = Unit

actual fun platformShareFile(fileName: String, mimeType: String, text: String) = Unit

actual fun platformClearExports() = Unit

actual fun processMemoryBytes(): Long? = MacMemory.physFootprint()

actual val processMemoryTargetBytes: Long = 1024L * 1024 * 1024

@OptIn(ExperimentalComposeUiApi::class)
actual fun plainTextClip(text: String): ClipEntry = ClipEntry(StringSelection(text))

actual fun Modifier.platformGestureExclusion(): Modifier = this

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.platformBackKeys(onBack: () -> Unit): Modifier = this
  .onKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == Shortcuts.Back.key) {
      onBack()
      true
    } else {
      false
    }
  }
  .onPointerEvent(PointerEventType.Press) { event ->
    if (event.button == PointerButton.Back) onBack()
  }


actual val platformIsTouch: Boolean = false

actual val platformPostSizes: PostSizes = PostSizes.Desktop

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual val platformCellGap: Dp = 12.dp

actual val platformGridEdge: Dp = 12.dp

@Composable
actual fun PlatformSystemBars(hidden: Boolean, lightIcons: Boolean) = Unit

@Composable
actual fun PlatformHideInRecents(hide: Boolean) = Unit

@Composable
actual fun PlatformPredictiveBackHandler(enabled: Boolean, onProgress: (Float) -> Unit, onCancel: () -> Unit, onBack: () -> Unit) = Unit

actual fun platformEnvironment(): AppEnvironment {
  val osName = System.getProperty("os.name").orEmpty()
  val mac = osName.startsWith("Mac", ignoreCase = true)
  val platform = if (mac) "macOS" else osName
  return AppEnvironment(
    version = APP_VERSION,
    platform = platform,
    os = "$platform ${System.getProperty("os.version").orEmpty()}".trim(),
    device = (if (mac) MacModel.describe() else null) ?: System.getProperty("os.arch").orEmpty(),
  )
}
