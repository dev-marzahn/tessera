package dev.marzahn.sankaku.tabs

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class FoundOn(
  val search: List<String>,
  val way: FindWay = FindWay.Grid,
  val at: Long = 0,
)

@Serializable
enum class FindWay {
  Post,

  Grid,
}

internal fun foundRowText(found: FoundOn, names: TagNameLookup = NoTagNames): String = when (found.way) {
  FindWay.Post -> "from a post in ${foundName(found, names)}"
  FindWay.Grid -> "from ${foundName(found, names)}"
}

internal fun foundCardText(found: FoundOn, now: Instant, zone: TimeZone, names: TagNameLookup = NoTagNames): String {
  val where = when (found.way) {
    FindWay.Post -> "a post in ${foundName(found, names)}"
    FindWay.Grid -> foundName(found, names)
  }
  return "Found on $where · ${foundDay(Instant.fromEpochSeconds(found.at), now, zone)}"
}

internal fun foundWayText(found: FoundOn): String = when (found.way) {
  FindWay.Post -> "from a post"
  FindWay.Grid -> "from the grid"
}

internal fun foundName(found: FoundOn, names: TagNameLookup = NoTagNames): String = tabTitle(searchSource(found.search), names = names)

internal fun foundDay(at: Instant, now: Instant, zone: TimeZone): String {
  val day = at.toLocalDateTime(zone).date
  val today = now.toLocalDateTime(zone).date
  val days = today.toEpochDays() - day.toEpochDays()
  return when {
    days <= 0L -> "today"
    days == 1L -> "yesterday"
    days < 7L -> weekday(day.dayOfWeek)
    else -> shortDate(day)
  }
}

internal fun keptWords(at: Instant, now: Instant, zone: TimeZone, hours24: Boolean = true): String? {
  if (at.epochSeconds == 0L) return null
  val time = at.toLocalDateTime(zone)
  if (time.date == now.toLocalDateTime(zone).date) return "kept ${clockTime(time.hour, time.minute, hours24)}"
  return "kept ${foundDay(at, now, zone)}"
}

internal fun clockTime(hour: Int, minute: Int, hours24: Boolean): String {
  val mm = minute.toString().padStart(2, '0')
  if (hours24) return "${hour.toString().padStart(2, '0')}:$mm"
  val twelve = if (hour % 12 == 0) 12 else hour % 12
  return "$twelve:$mm ${if (hour < 12) "AM" else "PM"}"
}

private fun weekday(day: DayOfWeek): String = day.name.lowercase().replaceFirstChar { it.uppercase() }

private fun shortDate(day: LocalDate): String =
  "${day.day} ${day.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
