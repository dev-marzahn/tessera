package dev.marzahn.sankaku.di

import dev.marzahn.sankaku.CACHE_FILE
import dev.marzahn.sankaku.FEED_MARKS_FILE
import dev.marzahn.sankaku.FIRST_LAUNCH_FILE
import dev.marzahn.sankaku.GRID_FILE
import dev.marzahn.sankaku.KNOWN_TAGS_FILE
import dev.marzahn.sankaku.OUTBOX_FILE
import dev.marzahn.sankaku.PLAYER_FILE
import dev.marzahn.sankaku.PRIVACY_FILE
import dev.marzahn.sankaku.QUEUE_FILE
import dev.marzahn.sankaku.READING_FILE
import dev.marzahn.sankaku.REOPEN_HINT_FILE
import dev.marzahn.sankaku.SEEN_FILE
import dev.marzahn.sankaku.TIPS_FILE
import dev.marzahn.sankaku.TODAY_FILE
import dev.marzahn.sankaku.UPDATES_FILE
import dev.marzahn.sankaku.data.TextFiles
import dev.marzahn.sankaku.tabs.FileTabStateStore
import dev.marzahn.sankaku.tabs.TabStateStore
import org.koin.dsl.module

val storedFilesModule = module {
  mapOf(
    KNOWN_TAGS_FILE to "tags.json",
    PLAYER_FILE to "player.json",
    REOPEN_HINT_FILE to "reopen-hint.json",
    PRIVACY_FILE to "privacy.json",
    FIRST_LAUNCH_FILE to "first-launch.json",
    QUEUE_FILE to "queue.json",
    GRID_FILE to "grid.json",
    CACHE_FILE to "cache.json",
    UPDATES_FILE to "updates.json",
    TIPS_FILE to "tips.json",
    SEEN_FILE to SEEN_FILE_NAME,
    FEED_MARKS_FILE to "feed-marks.json",
    TODAY_FILE to "today.json",
    READING_FILE to "readings.json",
    OUTBOX_FILE to "outbox.json",
  ).forEach { (file, name) -> single(file) { get<TextFiles>().named(name) } }
  single<TabStateStore> {
    val files = get<TextFiles>()
    FileTabStateStore(files.named(TABS_FILE_NAME), backup = files.named("tabs.v1.bak"), broken = files.named("tabs.json.broken"))
  }
}

const val TABS_FILE_NAME = "tabs.json"

const val SEEN_FILE_NAME = "seen.json"
