package dev.marzahn.sankaku.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import dev.marzahn.sankaku.data.TextFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Tip(val text: String, val touch: Boolean = true) {
  GridGestures("Long-press a post to keep it."),

  KeepLoop("Middle-click or E keeps a post for later · X closes it and moves on.", touch = false),

  QueueSwipe("Swipe sideways for the next item in your queue."),

  TabList("Tap the count to see your whole queue."),

  ReopenHold("Hold the queue count to reopen closed items any time."),

  LongPressFavourite("Long-press the picture to favourite."),

  OpenedInFront("Searches you open from a post open in front of where you were. Back takes you there, and so does finishing them."),

  KeptWaits("Kept posts wait right after their search. Swipe sideways to go through them."),

  WayBack("Back where you opened that search. It stays in your queue."),
}

fun openedInFrontText(title: String): String =
  "$title opened in front of where you were. Back takes you there, and so does finishing $title."

fun keptWaitsText(source: String, books: Boolean = false): String =
  "Kept ${if (books) "books" else "posts"} wait right after $source. Swipe sideways to go through them."

fun wayBackText(source: String): String = "Back where you opened $source. $source stays in your queue."

fun Tips.offerLongPressTip(wasFavourited: Boolean, ran: Boolean): Boolean =
  !wasFavourited && ran && tell(Tip.LongPressFavourite)

data class TipBubble(val tip: Tip, val text: String, val action: TipAction?, val anchor: Rect)

class TipAction(val label: String, val onClick: () -> Unit)

@Serializable
private data class SavedTips(val done: Set<String> = emptySet())

class Tips(private val file: TextFile, private val touch: Boolean) {
  private var done: Set<String> =
    file.read()?.let { runCatching { json.decodeFromString(SavedTips.serializer(), it).done }.getOrNull() } ?: emptySet()

  var current by mutableStateOf<Tip?>(null)
    private set

  private var onScreen: Tip? = null

  var bubble by mutableStateOf<TipBubble?>(null)
    private set

  var resting by mutableStateOf(false)
    private set

  var covered by mutableStateOf(false)

  var topBarBottom by mutableStateOf(0f)

  private var visit: Any? = null

  var visitSpent by mutableStateOf(false)
    private set

  private var tipUnderGesture: Tip? = null

  fun visiting(key: Any?) {
    if (key == visit) return
    visit = key
    onScreen?.let { dismissed(it) }
    resting = false
    visitSpent = false
  }

  private var tabCount: Int? = null

  var secondTabAppeared by mutableStateOf(false)
    private set

  fun counted(tabs: Int) {
    val before = tabCount
    tabCount = tabs
    if (before != null && before < 2 && tabs >= 2) secondTabAppeared = true
  }

  fun pending(tip: Tip): Boolean = tip.touch == touch && tip.name !in done

  fun offer(tip: Tip): Boolean {
    if (!pending(tip) || current != null || visitSpent) return false
    current = tip
    return true
  }

  fun tell(tip: Tip): Boolean {
    if (!pending(tip) || visitSpent) return false
    markDone(tip)
    visitSpent = true
    return true
  }

  fun appeared(tip: Tip, text: String = tip.text, action: TipAction? = null, anchor: Rect = Rect.Zero): Boolean {
    if (visitSpent) {
      dismissed(tip)
      return false
    }
    onScreen = tip
    visitSpent = true
    bubble = TipBubble(tip, text, action, anchor)
    if (tip.name !in done) markDone(tip)
    return true
  }

  fun moved(tip: Tip, text: String, action: TipAction?, anchor: Rect) {
    val shown = bubble ?: return
    if (shown.tip != tip) return
    if (shown.text != text || shown.action?.label != action?.label || shown.anchor != anchor) {
      bubble = TipBubble(tip, text, action, anchor)
    }
  }

  fun gestureStarted() {
    tipUnderGesture = onScreen
  }

  fun gestureEnded() {
    tipUnderGesture?.let { dismissed(it) }
    tipUnderGesture = null
    resting = false
  }

  fun used(tip: Tip) {
    if (tip.name !in done) markDone(tip)
  }

  fun dismissed(tip: Tip) {
    if (current == tip) current = null
    if (onScreen == tip) {
      onScreen = null
      bubble = null
      resting = true
    }
  }

  private fun markDone(tip: Tip) {
    done = done + tip.name
    file.write(json.encodeToString(SavedTips.serializer(), SavedTips(done)))
  }

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
  }
}
