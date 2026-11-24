package dev.marzahn.sankaku

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.unit.Dp
import dev.marzahn.sankaku.data.PostSizes
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.log.AppEnvironment
import org.koin.core.qualifier.named

data class DebugFlags(
  val sample: Boolean = false,
  val expireAccessToken: Boolean = false,
  val safeOnly: Boolean = false,
  val expireSession: Boolean = false,
  val crash: DebugCrash? = null,
  val openBook: String? = null,
)

enum class DebugCrash {
  Ui,
  Thread,
  ;

  companion object {
    fun parse(value: String?): DebugCrash? = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
  }
}

const val SAFE_ONLY_PROPERTY = "debug.tessera.safeOnly"

fun safeOnlyRequested(extra: Boolean, property: String?): Boolean = extra || property?.trim().toBoolean()

val CACHE_DIR = named("cacheDir")

val KNOWN_TAGS_FILE = named("knownTagsFile")

val PLAYER_FILE = named("playerFile")

val ACCESSIBILITY_FILE = named("accessibilityFile")

val QUEUE_FILE = named("queueFile")

val GRID_FILE = named("gridFile")

val CACHE_FILE = named("cacheFile")

val REOPEN_HINT_FILE = named("reopenHintFile")

val PRIVACY_FILE = named("privacyFile")

val UPDATES_FILE = named("updatesFile")

val SEEN_FILE = named("seenFile")

val FEED_MARKS_FILE = named("feedMarksFile")

val TODAY_FILE = named("todayFile")

val READING_FILE = named("readingFile")

val OUTBOX_FILE = named("outboxFile")

val TIPS_FILE = named("tipsFile")

val FIRST_LAUNCH_FILE = named("firstLaunchFile")

expect val platformHasTabs: Boolean

@Composable
expect fun platformUses24HourClock(): Boolean

enum class OpenUrlResult { Opened, Failed }

expect fun platformOpenUrl(url: String): OpenUrlResult

const val NoAppForLink = "No app can open this link"

fun Messages.openUrl(url: String, open: (String) -> OpenUrlResult = ::platformOpenUrl) {
  if (open(url) == OpenUrlResult.Failed) showError(NoAppForLink)
}

expect fun platformShareText(text: String)

expect fun platformShareFile(fileName: String, mimeType: String, text: String)

expect fun platformClearExports()

expect fun platformAppDataText(name: String): String?

expect fun plainTextClip(text: String): ClipEntry

expect fun processMemoryBytes(): Long?

expect val processMemoryTargetBytes: Long

expect fun Modifier.platformBackKeys(onBack: () -> Unit): Modifier

expect fun Modifier.platformGestureExclusion(): Modifier

@Composable
expect fun GridScrollbar(state: LazyStaggeredGridState, postCount: Int, modifier: Modifier = Modifier)

expect val gridScrollbarRoom: Dp

expect val platformIsTouch: Boolean

expect val platformPostSizes: PostSizes

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

expect val platformCellGap: Dp

expect val platformGridEdge: Dp

@Composable
expect fun PlatformSystemBars(hidden: Boolean, lightIcons: Boolean)

@Composable
expect fun PlatformHideInRecents(hide: Boolean)

@Composable
expect fun PlatformPredictiveBackHandler(enabled: Boolean, onProgress: (Float) -> Unit, onCancel: () -> Unit, onBack: () -> Unit)

expect fun platformEnvironment(): AppEnvironment
