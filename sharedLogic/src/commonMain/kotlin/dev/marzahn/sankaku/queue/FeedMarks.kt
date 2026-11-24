package dev.marzahn.sankaku.queue

import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.PageCursor
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.math.abs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Serializable
data class FeedMark(val post: String, val time: Long, val cursor: String? = null, val page: Int = 1) {
  val place: GridPlace get() = GridPlace(post, cursor, page, time)
}

@Serializable
data class FeedGap(
  val stop: FeedMark,
  val until: FeedMark,
  val left: Int? = null,
  val since: Long,
  val offered: Boolean = false,
  val read: Int = 0,
  val readEnd: FeedMark? = null,
) {
  internal fun unread(posts: List<FeedPost>, from: Int, until: Int): Int {
    val readBelow = readEnd != null && read > 0 && from < posts.size && posts[from].time > readEnd.time
    return (until - from - if (readBelow) read else 0).coerceAtLeast(0)
  }
}

@Serializable
data class FeedMarks(val top: FeedMark, val gap: FeedGap? = null, val at: Long, val reached: FeedReached = FeedReached())

@Serializable
data class TimeSpan(val from: Long, val to: Long, val read: Long, val context: String)

@Serializable
data class FeedReached(val posts: Map<String, Long> = emptyMap(), val spans: List<TimeSpan> = emptyList()) {
  fun lateAt(time: Long, context: String?): Boolean = context != null && spans.any {
    it.context == context && time in it.from..it.to && it.read - time <= LATE_WITHIN.inWholeSeconds
  }

  fun with(reached: List<FeedPost>, now: Long, context: String?): FeedReached {
    if (reached.isEmpty()) return this
    val added = posts + reached.associate { it.id to it.time }
    val span = context?.let { TimeSpan(reached.minOf { it.time }, reached.maxOf { it.time }, now, it) }
    var kept = added
    var merged = merge(spans + listOfNotNull(span))
    if (added.size > MAX_POSTS) {
      val sorted = added.entries.sortedByDescending { it.value }
      kept = sorted.take(MAX_POSTS).associate { it.key to it.value }
      val floor = sorted[MAX_POSTS].value + 1
      merged = merged.filter { it.to >= floor }.map { if (it.from < floor) it.copy(from = floor) else it }
    }
    return FeedReached(kept, merged.sortedByDescending { it.read }.take(MAX_SPANS))
  }

  companion object {
    const val MAX_POSTS = 600

    const val MAX_SPANS = 100

    val LATE_WITHIN: Duration = 2.days

    val MERGE_WITHIN: Duration = 1.hours

    private fun merge(spans: List<TimeSpan>): List<TimeSpan> {
      val out = mutableListOf<TimeSpan>()
      for (span in spans.sortedBy { it.from }) {
        val at = out.indexOfLast { it.context == span.context && span.from <= it.to && abs(it.read - span.read) <= MERGE_WITHIN.inWholeSeconds }
        if (at >= 0) {
          val into = out[at]
          out[at] = into.copy(to = maxOf(into.to, span.to), read = maxOf(into.read, span.read))
        } else {
          out += span
        }
      }
      return out
    }
  }
}

data class FeedPost(val id: String, val time: Long, val cursor: PageCursor = PageCursor()) {
  val mark: FeedMark get() = FeedMark(id, time, cursor.next, cursor.number)
}

sealed interface FeedHeading {
  data class New(
    val count: Int,
    val more: Boolean,
    val gapLeft: Int?,
    val hasGap: Boolean = gapLeft != null,
    val late: Int = 0,
    val firstLate: Int? = null,
  ) : FeedHeading

  data object CaughtUp : FeedHeading

  data class UpToDate(val gapLeft: Int?, val hasGap: Boolean = gapLeft != null) : FeedHeading

  data class Continue(val left: Int?, val offer: Boolean) : FeedHeading

  data class LikedToday(val count: Int?) : FeedHeading

  data object Earlier : FeedHeading
}

data class PlacedHeading(val before: Int, val heading: FeedHeading)

object FeedReading {
  val FOR_YOU_KEPT: Duration = 3.days

  val GAP_OFFER_AFTER: Duration = 7.days

  const val NEW_COUNT_LIMIT = 200

  fun expired(feed: Feed, marks: FeedMarks, now: Instant): Boolean =
    feed == Feed.ForYou && now - Instant.fromEpochSeconds(marks.at) > FOR_YOU_KEPT

  fun headings(
    feed: Feed,
    posts: List<FeedPost>,
    atTop: Boolean,
    marks: FeedMarks?,
    now: Instant,
    context: String? = null,
  ): List<PlacedHeading> {
    if (feed != Feed.Following || marks == null || posts.isEmpty()) return emptyList()
    val headings = mutableListOf<PlacedHeading>()
    val gap = marks.gap?.takeUnless { g ->
      val stopAfter = after(posts, g.stop)
      val until = at(posts, g.until)
      stopAfter != null && until != null && until <= stopAfter
    }
    val stopAfter = gap?.let { after(posts, it.stop) }
    val until = gap?.let { at(posts, it.until) }
    val left = if (gap != null && stopAfter != null && until != null) gap.unread(posts, stopAfter, until) else gap?.left
    if (atTop) {
      val top = at(posts, marks.top)
      val late = lateIndices(posts, atTop = true, marks, context)
      when {
        top == null -> headings += PlacedHeading(0, newHeading(posts.size, more = true, gap, left))
        top > 0 -> {
          headings += PlacedHeading(0, newHeading(top, more = false, gap, left, late))
          headings += PlacedHeading(top, FeedHeading.CaughtUp)
        }
        late.isNotEmpty() -> headings += PlacedHeading(0, newHeading(0, more = false, gap, left, late))
        else -> headings += PlacedHeading(0, FeedHeading.UpToDate(left, hasGap = gap != null))
      }
    }
    if (gap != null && stopAfter != null) {
      val offer = !gap.offered && now - Instant.fromEpochSeconds(gap.since) >= GAP_OFFER_AFTER
      headings += PlacedHeading(stopAfter, FeedHeading.Continue(left, offer))
      if (until != null && headings.none { it.before == until }) headings += PlacedHeading(until, FeedHeading.CaughtUp)
    }
    return headings.sortedBy { it.before }
  }

  private fun newHeading(count: Int, more: Boolean, gap: FeedGap?, left: Int?, late: List<Int> = emptyList()) =
    FeedHeading.New(count + late.size, more, left, hasGap = gap != null, late = late.size, firstLate = late.firstOrNull())

  fun late(feed: Feed, posts: List<FeedPost>, atTop: Boolean, marks: FeedMarks?, context: String?): Set<String> {
    if (feed != Feed.Following || marks == null) return emptySet()
    return lateIndices(posts, atTop, marks, context).mapTo(mutableSetOf()) { posts[it].id }
  }

  fun context(searched: List<String>, followed: Collection<String>): String =
    searched.sorted().joinToString(" ") + "|" + followed.sorted().joinToString(",").hashCode().toUInt().toString(16)

  private fun lateIndices(posts: List<FeedPost>, atTop: Boolean, marks: FeedMarks, context: String?): List<Int> {
    val reached = marks.reached
    if (reached.spans.isEmpty() || posts.isEmpty()) return emptyList()
    val top = at(posts, marks.top)
    val below = when {
      top != null -> top + 1
      !atTop && posts.first().time <= marks.top.time -> 0
      else -> return emptyList()
    }
    return (below until posts.size).filter { i ->
      val post = posts[i]
      post.id != marks.top.post && post.id !in reached.posts && reached.lateAt(post.time, context)
    }
  }

  internal fun at(posts: List<FeedPost>, mark: FeedMark): Int? {
    val index = posts.indexOfFirst { it.id == mark.post }
    if (index >= 0) return index
    if (posts.isEmpty() || posts.last().time >= mark.time) return null
    return posts.indexOfFirst { it.time < mark.time }
  }

  internal fun after(posts: List<FeedPost>, mark: FeedMark): Int? {
    val index = posts.indexOfFirst { it.id == mark.post }
    if (index >= 0) return (index + 1).takeIf { it < posts.size }
    return at(posts, mark)
  }
}

class FeedVisit(
  private val feed: Feed,
  private val start: FeedMarks?,
  private val now: Instant,
  var context: String? = null,
) {
  private var newest: FeedPost? = null

  private var newDeepest: FeedPost? = null
  private var newLeft: Int? = null
  private var caughtUp = false

  private var readStretch: Int? = null

  private var gapDeepest: FeedPost? = null
  private var gapLeft: Int? = start?.gap?.left
  private var gapClosed = false

  private var offerShown = false
  private var answered = false
  private var letGo = false

  private var reached = start?.reached ?: FeedReached()

  val shown: FeedMarks?
    get() = start?.let { s ->
      when {
        letGo -> s.copy(gap = null)
        answered -> s.copy(gap = s.gap?.copy(offered = true))
        else -> s
      }
    }

  fun reach(posts: List<FeedPost>, atTop: Boolean, first: Int, last: Int) {
    if (posts.isEmpty() || first > last) return
    val from = first.coerceIn(posts.indices)
    val to = last.coerceIn(posts.indices)
    if (feed == Feed.ForYou && newest == null || atTop && newer(posts[from], newest)) newest = posts[from]
    if (feed == Feed.Following) reached = reached.with(posts.subList(from, to + 1), now.epochSeconds, context)
    if (start == null || feed != Feed.Following) return
    val deepest = posts[to]
    start.gap?.let { gap ->
      val top = FeedReading.at(posts, start.top)
      val stopAfter = FeedReading.after(posts, gap.stop)
      if (readStretch == null && top != null && stopAfter != null) readStretch = stopAfter - top
    }
    if (atTop && !caughtUp) {
      val top = FeedReading.at(posts, start.top)
      if (top != null && to >= top) {
        caughtUp = true
      } else if (newDeepest.let { it == null || older(deepest, it) }) {
        newDeepest = deepest
        newLeft = top?.let { it - to - 1 }
      }
    }
    val gap = start.gap ?: return
    if (gapClosed) return
    val until = FeedReading.at(posts, gap.until)
    if (until != null && to >= until && posts.first().time > gap.until.time) {
      gapClosed = true
      return
    }
    val inGap = deepest.time < gap.stop.time && deepest.time > gap.until.time
    if (inGap && gapDeepest.let { it == null || older(deepest, it) }) {
      gapDeepest = deepest
      gapLeft = leftIn(posts, gap, to, until)
    }
  }

  fun offerShown() {
    offerShown = true
  }

  fun keep() {
    answered = true
    offerShown = true
  }

  fun letGo() {
    letGo = true
    answered = true
    offerShown = true
  }

  val marks: FeedMarks?
    get() {
      val top = newest?.takeIf { start == null || newer(it, start.top) }?.mark ?: start?.top ?: return null
      if (feed != Feed.Following) return FeedMarks(top, null, now.epochSeconds)
      if (start == null) return FeedMarks(top, null, now.epochSeconds, reached)
      var gap = start.gap
      if (letGo || gapClosed) gap = null
      gap = gap?.let { g ->
        gapDeepest?.let { g.copy(stop = it.mark, left = gapLeft) } ?: g
      }?.let { if (offerShown) it.copy(offered = true) else it }
      val opened = newest?.takeIf { newer(it, start.top) && !caughtUp }?.let { (newDeepest ?: it) }
      if (opened != null) {
        val left = if (opened == newDeepest) newLeft else null
        gap = if (gap == null) {
          FeedGap(stop = opened.mark, until = start.top, left = left, since = now.epochSeconds)
        } else {
          gap.copy(
            stop = opened.mark,
            left = if (left != null && gap.left != null) left + gap.left else null,
            read = gap.read + (readStretch ?: 0),
            readEnd = gap.readEnd ?: gap.stop,
          )
        }
      }
      return FeedMarks(top, gap, now.epochSeconds, reached)
    }

  private fun leftIn(posts: List<FeedPost>, gap: FeedGap, to: Int, until: Int?): Int? {
    if (until != null) return gap.unread(posts, to + 1, until)
    val stop = posts.indexOfFirst { it.id == gap.stop.post }
    val left = gap.left ?: return null
    return if (stop >= 0) (left - (to - stop)).coerceAtLeast(0) else null
  }

  private fun newer(post: FeedPost, than: FeedPost?): Boolean = than == null || post.time > than.time
  private fun newer(post: FeedPost, than: FeedMark): Boolean = post.id != than.post && post.time > than.time
  private fun older(post: FeedPost, than: FeedPost): Boolean = post.time < than.time
}
