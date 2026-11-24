package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.data.StartOn
import dev.marzahn.sankaku.queue.QueuePageAtStart
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

class QueueStart(
  private val tabs: TabsController,
  private val privacy: PrivacySettings,
  private val time: TimeSource.WithComparableMarks = TimeSource.Monotonic,
  private val upNext: UpNextState? = null,
) {
  var pageOpen by mutableStateOf(false)
    private set

  var interacted by mutableStateOf(false)
    private set

  internal val chipState = mutableStateOf(QueueFilter.All)

  internal var chip by chipState

  private var landedOn: TabId? = null
  private var launched = false
  private var leftAt: ComparableTimeMark? = null

  fun launched() {
    if (launched) return
    launched = true
    start()
  }

  fun left() {
    leftAt = time.markNow()
  }

  fun returned() {
    val left = leftAt ?: return
    leftAt = null
    if (left.elapsedNow() > AWAY) start()
  }

  fun shown(id: TabId) {
    if (id != landedOn) interact()
  }

  fun interact() {
    interacted = true
  }

  fun openPage() {
    pageOpen = true
    tabs.reachedAgain()
  }

  fun closePage() {
    pageOpen = false
  }

  private fun start() {
    upNext?.dismiss()
    chip = QueueFilter.All
    tabs.endKeptRun()
    tabs.reachedAgain()
    when (privacy.startOn.value) {
      StartOn.Queue -> pageOpen = QueuePageAtStart.opens(tabs.tabs.value.map { it.kind })
      StartOn.Browse -> {
        pageOpen = false
        tabs.showBrowse()
      }
    }
    landedOn = tabs.active.value
  }

  companion object {
    val AWAY = 5.minutes
  }
}
