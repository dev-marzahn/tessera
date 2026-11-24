package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.queue.SourceKey
import dev.marzahn.sankaku.ui.components.CountPillText
import org.koin.mp.KoinPlatform

class SortDeck(
  private val tabs: TabsController,
  private val undo: ClosedTabUndo,
  private val gOnly: () -> Boolean = { false },
  private val liked: () -> Set<String> = { emptySet() },
) {
  data class Tally(
    val later: Int = 0,
    val done: Int = 0,
    val followed: Int = 0,
    val laterWentIn: Int = 0,
    val doneWentIn: Int = 0,
    val followedWentIn: Int = 0,
    val undone: List<Undone> = emptyList(),
  ) {
    val sorted: Int get() = later + done + followed

    fun count(kind: Answer.Kind): Int = when (kind) {
      Answer.Kind.Later -> later
      Answer.Kind.Done -> done
      Answer.Kind.Followed -> followed
    }

    fun wentIn(kind: Answer.Kind): Int = when (kind) {
      Answer.Kind.Later -> laterWentIn
      Answer.Kind.Done -> doneWentIn
      Answer.Kind.Followed -> followedWentIn
    }
  }

  data class Undone(val kind: Answer.Kind, val title: String)

  var running by mutableStateOf(false)
    private set

  var shown by mutableStateOf(false)
    private set

  var stopped by mutableStateOf(false)
    private set

  private val cards = mutableStateListOf<TabId>()

  var position by mutableIntStateOf(0)
    private set

  val total: Int get() = cards.size

  var goneIn: TabId? by mutableStateOf(null)
    private set

  var inKept by mutableStateOf(false)
    private set

  private val answers = mutableStateMapOf<Int, Answer.Kind>()

  private val undone = mutableStateMapOf<Int, Undone>()

  private val likedInside = mutableStateMapOf<Int, Set<String>>()
  private var likedAtGoIn: Set<String> = emptySet()

  val tally: Tally get() {
    val answered = answers.entries
    fun of(kind: Answer.Kind) = answered.count { it.value == kind }
    fun wentIn(kind: Answer.Kind) = answered.count { it.value == kind && it.key in likedInside }
    return Tally(
      later = of(Answer.Kind.Later),
      done = of(Answer.Kind.Done),
      followed = of(Answer.Kind.Followed),
      laterWentIn = wentIn(Answer.Kind.Later),
      doneWentIn = wentIn(Answer.Kind.Done),
      followedWentIn = wentIn(Answer.Kind.Followed),
      undone = undone.entries.sortedBy { it.key }.map { it.value },
    )
  }

  private class LastDone(val position: Int, val id: TabId, val title: String, val closed: ClosedTab, val kind: Answer.Kind) : AnswerLine

  private var lastDone: LastDone? by mutableStateOf(null)

  private class LastLater(val position: Int, val id: TabId, val title: String, val after: List<TabId>, val stillNew: Boolean) : AnswerLine

  private var lastLater: LastLater? by mutableStateOf(null)

  private val titles = mutableMapOf<TabId, String>()

  data class Answer(val kind: Kind, val title: String, val stillNew: Boolean = true, val kept: Int = 0) {
    enum class Kind { Later, Done, Followed }
  }

  val lastAnswer: Answer? get() {
    lastDone?.takeIf { canUndoDone }?.let {
      return Answer(it.kind, it.title, kept = it.closed.keptStay)
    }
    return lastLater?.let { Answer(Answer.Kind.Later, it.title, stillNew = it.stillNew) }
  }

  sealed interface AnswerLine

  val line: AnswerLine? get() = lastDone ?: lastLater

  fun endLine(line: AnswerLine) {
    if (lastDone === line) lastDone = null
    if (lastLater === line) lastLater = null
  }

  fun undoAnswer() {
    if (lastDone != null) undoDone() else undoLater()
  }

  fun undoLater() {
    val last = lastLater ?: return
    lastLater = null
    if (open(last.id) == null) return
    last.after.firstOrNull { open(it) != null }?.let { tabs.moveInLine(last.id, it) }
    backToCard(last.position, Undone(Answer.Kind.Later, last.title))
  }

  private val posts = mutableStateMapOf<TabId, CardPosts>()

  fun postsFor(tab: TabState, searched: List<String>): CardPosts? = posts[tab.id]?.takeIf { it.searched == searched }

  fun shownPosts(tab: TabState, searched: List<String>, count: Int): CardPosts? {
    val own = postsFor(tab, searched)
    if (own !is CardPosts.Loaded) return own
    val at = cards.indexOf(tab.id)
    val front = card
    if (front != null && at > position && posts[front.id] == null) return null
    return own.copy(posts = shownAfter(own.posts, shownBefore(at, count), count))
  }

  private fun shownBefore(at: Int, count: Int): List<PostDto> {
    val openIds = tabs.tabs.value.mapTo(HashSet()) { it.id }
    val met = (0 until at).filter { it in answers || cards[it] in openIds }
    return met.takeLastWhile { posts[cards[it]] is CardPosts.Loaded }
      .fold(emptyList()) { shown, i -> shownAfter((posts[cards[i]] as CardPosts.Loaded).posts, shown, count) }
  }

  val card: TabState? get() = if (stopped || !running) null else cards.getOrNull(position)?.let(::open)

  val nextCard: TabState? get() = if (!running) null else cards.drop(position + 1).firstNotNullOfOrNull(::open)

  val left: Int get() = cards.drop(position).count { open(it) != null }

  data class WentIn(val progress: SourceProgress?, val kept: Int, val liked: Int)

  val wentIn: WentIn? get() {
    val tab = card ?: return null
    val liked = likedInside[position] ?: return null
    return WentIn(sourceProgress(tab), tabs.keptPostsOf(tab), liked.size)
  }

  val stillNew: Int get() {
    val newIds = newSources(tabs.tabs.value, gOnly()).mapTo(mutableSetOf()) { it.id }
    return cards.count { it in newIds }
  }

  fun start() {
    while (running) end()
    begin(newSources(tabs.tabs.value, gOnly()), null)
  }

  fun startFinds(finds: List<TabState>, next: TabId?, then: TabId?, foundOn: String? = null) =
    begin(finds, FindsRun(next, then, foundOn))

  private fun begin(sources: List<TabState>, finds: FindsRun?) {
    if (sources.isEmpty()) return
    when {
      finds != null && running && findsRun == null -> sortUnderFinds = save()
      finds == null -> sortUnderFinds = null
    }
    val from = finds?.then ?: tabs.active.value
    clear()
    cards.addAll(sources.map { it.id })
    sources.associateTo(titles) { it.id to it.title }
    findsRun = finds
    startedFrom = from
    running = true
    shown = true
    settle()
  }

  private class FindsRun(val next: TabId?, val then: TabId?, val foundOn: String?) {
    val back: Boolean get() = next == null
  }

  private var findsRun: FindsRun? by mutableStateOf(null)

  private var startedFrom: TabId? = null

  val goesOnTo: TabState? get() = findsRun?.then?.let(::open)

  val goesBack: Boolean get() = findsRun?.back == true

  val foundOn: String? get() = findsRun?.foundOn

  suspend fun loadPosts(tab: TabState, searched: List<String>, previews: SourcePreviews) {
    if (postsFor(tab, searched) != null) return
    posts.remove(tab.id)
    posts[tab.id] = previews.cardPosts(tab, searched)?.let { CardPosts.Loaded(searched, it) } ?: CardPosts.Failed(searched)
  }

  fun goIn() {
    val source = card ?: return
    findsRun?.next?.let { tabs.moveInLine(source.id, it) }
    goneIn = source.id
    likedAtGoIn = liked()
    if (position !in likedInside) likedInside[position] = emptySet()
    lastDone = null
    lastLater = null
    shown = false
    tabs.activate(source.id)
  }

  fun later() {
    val source = card ?: return
    answerLater(source.id)
    advance()
  }

  fun done() {
    val source = card ?: return
    answerDone(source.id, tabs.close(source.id) ?: return)
    advance()
  }

  val canUndoDone: Boolean get() = lastDone?.closed?.let { it == undo.pending.value?.closed } == true

  fun undoDone() {
    val last = lastDone ?: return
    if (!canUndoDone) return
    undo.undo()
    lastDone = null
    cardBack(last.position, last.id, last.closed) ?: return
    backToCard(last.position, Undone(last.kind, last.title))
  }

  private fun cardBack(at: Int, id: TabId, closed: ClosedTab): TabState? {
    val reopened = (closed.top as? Route.Grid)?.let { SourceKey.of(it.tags) }?.let(tabs::sourceFor) ?: return null
    cards[at] = reopened.id
    titles[reopened.id] = reopened.title
    posts.remove(id)?.let { posts[reopened.id] = it }
    return reopened
  }

  private fun undoneInKept(at: Int, id: TabId, closed: ClosedTab, kind: Answer.Kind) {
    if (!inKept || goneIn != id || position != at) return
    val reopened = cardBack(at, id, closed) ?: return
    answers.remove(at)
    undone[at] = Undone(kind, titles[id] ?: closed.title)
    lastDone = null
    inKept = false
    goneIn = reopened.id
  }

  private fun backToCard(at: Int, taken: Undone) {
    position = at
    answers.remove(at)
    undone[at] = taken
    stopped = false
  }

  fun followedInstead(id: TabId, alsoUndo: () -> Unit) {
    val wasCard = !stopped && cards.getOrNull(position) == id && goneIn == null
    val wasGoneIn = goneIn == id && !inKept
    val closed = tabs.close(id) ?: return
    if (!wasCard && !wasGoneIn) {
      undo.opened(closed, alsoUndo = alsoUndo)
      return
    }
    if (wasGoneIn) {
      closedFromGrid(id, closed, Answer.Kind.Followed, alsoUndo)
    } else {
      answerDone(id, closed, alsoUndo, followed = true)
      advance()
    }
  }

  fun backToDeck() {
    if (goneIn == null) return
    if (inKept) backOnTheNextCard() else showDeck()
  }

  private fun showDeck() {
    leaveSource()
    shown = true
    settle()
  }

  private fun leaveSource() {
    if (goneIn == null) return
    likedInside[position] = likedInside[position].orEmpty() + (liked() - likedAtGoIn)
    goneIn = null
    inKept = false
  }

  fun closeAndNext() {
    val source = goneIn?.takeUnless { inKept } ?: return
    closedFromGrid(source, tabs.close(source) ?: return, Answer.Kind.Done, alsoUndo = null)
  }

  private fun closedFromGrid(source: TabId, closed: ClosedTab, kind: Answer.Kind, alsoUndo: (() -> Unit)?) {
    val landedOnKept = closed.keptStay > 0 && tabs.activeTab.block == source
    val at = position
    val undoAll: (() -> Unit)? = if (landedOnKept) {
      {
        undoneInKept(at, source, closed, kind)
        alsoUndo?.invoke()
      }
    } else {
      alsoUndo
    }
    answerDone(source, closed, undoAll, followed = kind == Answer.Kind.Followed)
    if (landedOnKept) inKept = true else backOnTheNextCard()
  }

  fun laterFromGrid() {
    val source = goneIn ?: return
    answerLater(source)
    backOnTheNextCard()
  }

  fun itemShown(id: TabId) {
    val source = goneIn ?: return
    if (inKept) {
      if (pastBlock(source, id)) backOnTheNextCard()
      return
    }
    if (open(source) == null) {
      val closed = undo.pending.value?.closed?.takeIf { closed -> closed.members.any { it.id == source } }
      answerDone(source, closed)
      backOnTheNextCard()
      return
    }
    if (pastBlock(source, id)) backToDeck()
  }

  private fun pastBlock(source: TabId, id: TabId): Boolean {
    val all = tabs.tabs.value
    val end = all.indexOfLast { it.id == source || it.block == source }
    return end < 0 || all.indexOfFirst { it.id == id } > end
  }

  fun stop() {
    stopped = true
  }

  fun keepSorting() {
    stopped = false
    settle()
  }

  fun end() {
    val finds = findsRun != null
    val back = startedFrom
    clear()
    val run = sortUnderFinds.takeIf { finds }
    if (finds) sortUnderFinds = null
    back?.takeIf { it != tabs.active.value && open(it) != null }?.let(tabs::activate)
    if (run?.goneIn == null) return
    restore(run)
    if (open(run.goneIn) == null) itemShown(tabs.active.value)
  }

  private class SavedSortRun(
    val cards: List<TabId>,
    val position: Int,
    val answers: Map<Int, Answer.Kind>,
    val undone: Map<Int, Undone>,
    val likedInside: Map<Int, Set<String>>,
    val likedAtGoIn: Set<String>,
    val goneIn: TabId?,
    val inKept: Boolean,
    val posts: Map<TabId, CardPosts>,
    val lastDone: LastDone?,
    val lastLater: LastLater?,
    val titles: Map<TabId, String>,
    val startedFrom: TabId?,
  )

  private var sortUnderFinds: SavedSortRun? = null

  private fun save() = SavedSortRun(
    cards.toList(), position, answers.toMap(), undone.toMap(), likedInside.toMap(), likedAtGoIn, goneIn, inKept,
    posts.filterValues { it is CardPosts.Loaded }, lastDone, lastLater, titles.toMap(), startedFrom,
  )

  private fun restore(run: SavedSortRun) {
    clear()
    cards.addAll(run.cards)
    position = run.position
    answers.putAll(run.answers)
    undone.putAll(run.undone)
    likedInside.putAll(run.likedInside)
    likedAtGoIn = run.likedAtGoIn
    goneIn = run.goneIn
    inKept = run.inKept
    posts.putAll(run.posts)
    lastDone = run.lastDone
    lastLater = run.lastLater
    titles.putAll(run.titles)
    startedFrom = run.startedFrom
    running = true
  }

  private fun clear() {
    running = false
    shown = false
    stopped = false
    goneIn = null
    inKept = false
    cards.clear()
    posts.clear()
    answers.clear()
    undone.clear()
    likedInside.clear()
    likedAtGoIn = emptySet()
    titles.clear()
    lastLater = null
    lastDone = null
    position = 0
    findsRun = null
    startedFrom = null
  }

  fun pillText(): CountPillText? = if (running && goneIn != null) {
    val at = position + 1
    CountPillText(SORT, "$at / $total", "Sort, card $at of $total. Back to the deck")
  } else {
    null
  }

  private fun answerLater(id: TabId) {
    val all = tabs.tabs.value
    val tab = all.firstOrNull { it.id == id } ?: return
    val after = all.drop(all.indexOfLast { it.id == id || it.block == id } + 1).map { it.id }
    val stillNew = isNewSource(tab)
    if (!tabs.moveToEnd(id)) return
    answer(Answer.Kind.Later)
    lastDone = null
    lastLater = LastLater(position, id, tab.title, after, stillNew)
  }

  private fun answerDone(id: TabId, closed: ClosedTab?, alsoUndo: (() -> Unit)? = null, followed: Boolean = false) {
    closed?.let { undo.opened(it, alsoUndo = alsoUndo) }
    val kind = if (followed) Answer.Kind.Followed else Answer.Kind.Done
    answer(kind)
    lastLater = null
    lastDone = closed?.let { LastDone(position, id, titles[id] ?: it.title, it, kind) }
  }

  private fun answer(kind: Answer.Kind) {
    answers[position] = kind
    undone.remove(position)
  }

  private fun backOnTheNextCard() {
    leaveSource()
    shown = true
    advance()
  }

  private fun advance() {
    position++
    settle()
  }

  private fun settle() {
    while (position < cards.size && open(cards[position]) == null) position++
    if (position >= cards.size) stopped = true
  }

  private fun open(id: TabId): TabState? = tabs.tabs.value.firstOrNull { it.id == id }
}

sealed interface CardPosts {
  val searched: List<String>

  data class Loaded(override val searched: List<String>, val posts: List<PostDto>) : CardPosts {
    override fun toString() = "Loaded(posts=${posts.size})"
  }

  data class Failed(override val searched: List<String>) : CardPosts {
    override fun toString() = "Failed"
  }
}

internal fun shownAfter(posts: List<PostDto>, before: List<PostDto>, count: Int): List<PostDto> {
  val shown = before.mapTo(HashSet()) { it.id }
  return posts.filterNot { it.id in shown }.ifEmpty { posts }.take(count)
}

@Composable
internal fun sortDeckOrNull(): SortDeck? = remember { KoinPlatform.getKoinOrNull()?.getOrNull<SortDeck>() }

internal const val SORT = "Sort"

fun newSources(tabs: List<TabState>, gOnly: Boolean = false): List<TabState> =
  tabs.filter { it.head == it.id && isNewSource(it) && !(gOnly && isAdult(it.top) { null }) }
