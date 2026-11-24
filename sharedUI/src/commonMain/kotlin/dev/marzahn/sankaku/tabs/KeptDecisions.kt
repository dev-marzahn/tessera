package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.ui.components.CountPillText

enum class KeptRunKind(val pill: String, val spoken: String, val endPane: String) {
  Kept(KEPT_PILL, "Kept posts", KEPT_END_PANE),
  Later(LATER_PILL, "Later posts", LATER_END_PANE),
}

data class KeptProgress(val decided: Int, val total: Int, val later: Int) {
  val fraction: Float get() = if (total <= 0) 0f else (decided.toFloat() / total).coerceIn(0f, 1f)
}

data class KeptTally(val kind: KeptRunKind, val goneThrough: Int, val dropped: List<String?>, val later: Int)

internal data class KeptRun(
  val kind: KeptRunKind,
  val ids: List<TabId>,
  val decided: Set<TabId> = emptySet(),
  val dropped: Map<TabId, String?> = emptyMap(),
  val over: Boolean = false,
) {
  operator fun contains(id: TabId) = id in ids

  fun reopened(renamed: Map<TabId, TabId>, stillLater: (TabId) -> Boolean): KeptRun {
    val back = ids.any { it in renamed }
    return copy(
      ids = ids.map { renamed[it] ?: it },
      decided = decided.mapNotNullTo(mutableSetOf()) { id -> renamed[id]?.takeIf(stillLater) ?: id.takeIf { it !in renamed } },
      dropped = dropped.filterKeys { it !in renamed },
      over = over && !back,
    )
  }
}

internal fun laterPosts(tabs: List<TabState>, gOnly: Boolean, post: (TabState) -> TabPost?): List<TabState> =
  tabs.filter { it.kind.isKept && it.later && !(gOnly && isAdult(it, post(it))) }

internal fun decidedText(progress: KeptProgress): String = "${progress.decided} of ${progress.total}"

internal fun laterText(progress: KeptProgress): String? = progress.later.takeIf { it > 0 }?.let { "$it later" }

internal fun runPillText(kind: KeptRunKind, progress: KeptProgress): CountPillText {
  val later = laterText(progress).takeIf { kind == KeptRunKind.Kept }
  return CountPillText(
    kind.pill,
    decidedText(progress),
    "${kind.spoken}, ${progress.decided} of ${progress.total} gone through${later?.let { ", $it" }.orEmpty()}. Open the queue",
    later = later,
    progress = progress.fraction,
  )
}

internal const val LATER_PILL = "Later"

internal const val LATER_HINT_POSTS = 3
