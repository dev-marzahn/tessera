package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.queue.SessionId
import kotlin.time.Instant

internal fun keptStaySuffix(kept: Int): String = if (kept > 0) " · $kept kept stay" else ""

internal fun keptPosts(count: Int): String = "$count ${if (count == 1) "kept post" else "kept posts"}"

const val UNDO_CLOSE_LABEL = "Undo close"

private const val UNKEPT = "Removed from queue"

class ClosedTab internal constructor(
  members: List<Member>,
  internal val activeMember: Int? = null,
  val key: Long = nextKey++,
  val cleared: Boolean = false,
  val keptStay: Int = 0,
  private val tagNames: TagNameLookup = NoTagNames,
  internal val closedAt: Instant = Instant.DISTANT_PAST,
) {
  internal data class Member(
    val routes: List<Route>,
    val index: Int,
    val id: TabId? = null,
    val block: TabId? = null,
    val addedIn: SessionId = SessionId.EARLIER,
    val place: GridPlace? = null,
    val keptAt: Instant? = null,
    val closedSource: List<String>? = null,
    val title: String? = null,
    val gridTouched: Boolean = false,
    val mosaic: QueueMosaic? = null,
    val openedFrom: TabId? = null,
    val refines: List<String>? = null,
    val preview: SourcePreview? = null,
    val foundOn: FoundOn? = null,
    val wayBack: TabId? = null,
    val later: Boolean = false,
  ) {
    val top: Route? get() = routes.lastOrNull()
  }

  internal var members: List<Member> = members
    private set

  val size: Int get() = members.size

  val routes: List<Route> get() = members.first().routes

  internal val index: Int get() = members.first().index

  val top: Route? get() = routes.lastOrNull()

  val title: String
    get() = when {
      cleared -> if (size == 1) "1 post" else "$size posts"
      size == 1 -> tabTitle(top, members.first().title, tagNames)
      else -> batchTitle(size)
    }

  val untitled: Boolean get() = size == 1 && !cleared && tabIsUntitled(top, members.first().title)

  internal fun moved(place: (Int) -> Int) =
    ClosedTab(members.map { it.copy(index = place(it.index)) }, activeMember, key, cleared, keptStay, tagNames, closedAt)

  internal fun renamed(ids: Map<TabId, TabId>) = ClosedTab(
    members.map { member ->
      val reopened = member.wayBack?.let(ids::get)?.let { member.copy(wayBack = it) } ?: member
      val opener = reopened.openedFrom?.let(ids::get)?.let { reopened.copy(openedFrom = it) } ?: reopened
      val head = opener.block?.let(ids::get) ?: return@map opener
      opener.copy(block = head, closedSource = null)
    },
    activeMember, key, cleared, keptStay, tagNames, closedAt,
  )

  internal fun adopt(from: TabId, to: TabId) {
    members = members.map { if (it.block == from && it.closedSource != null) it.copy(block = to, closedSource = null) else it }
  }

  val closedLabel: String get() = if (size == 1) "Closed “$title”" else "Closed $size"

  override fun equals(other: Any?) = other is ClosedTab && other.key == key

  override fun hashCode() = key.hashCode()

  private companion object {
    var nextKey = 0L
  }
}

enum class Closing(internal val reopensInPlace: Boolean) {
  Closed(reopensInPlace = false),
  OnQueuePage(reopensInPlace = true),
  Unkept(reopensInPlace = true),
  Cleared(reopensInPlace = true),
  ;

  internal class Words(val shown: String, val spoken: String, val undo: String)

  internal fun words(closed: ClosedTab): Words = when (this) {
    Closed, OnQueuePage -> Words(
      shown = closed.closedLabel,
      spoken = (if (closed.size > 1) "Closed ${closed.size} items" else closed.closedLabel) + keptStaySuffix(closed.keptStay),
      undo = UNDO_CLOSE_LABEL,
    )
    Unkept -> Words(shown = UNKEPT, spoken = UNKEPT, undo = "Undo remove")
    Cleared -> "Cleared ${keptPosts(closed.size)}".let { Words(shown = it, spoken = it, undo = "Undo clear") }
  }

  companion object {
    internal fun of(closed: ClosedTab): Closing = if (closed.cleared) Cleared else Closed
  }
}
