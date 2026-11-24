package dev.marzahn.sankaku.tabs

internal fun dateRangeLabel(tag: String): String? {
  val range = tag.takeIf { it.startsWith("date:", ignoreCase = true) }?.drop("date:".length)?.split("..") ?: return null
  if (range.size != 2) return null
  val from = DateTime.of(range[0]) ?: return null
  val to = DateTime.of(range[1]) ?: return null
  if (from.instant >= to.instant) return null
  return when {
    from.time != null && to.time != null && from.day == to.day -> "${from.day}, ${from.time}–${to.time}"
    from.time == null && to.time == null && to.next(from) -> from.day
    else -> "${from.text()} – ${to.text()}"
  }
}

private class DateTime(val year: Int, val month: Int, val dayOfMonth: Int, val time: String?) {
  val day: String get() = "$dayOfMonth ${MONTHS[month - 1]} $year"

  val instant: Long get() = (((year * 100L + month) * 100 + dayOfMonth) * 100) + (time?.take(2)?.toInt() ?: 0)

  fun text() = if (time == null) day else "$day $time"

  fun next(other: DateTime): Boolean {
    val length = daysIn(other.year, other.month)
    return when {
      other.dayOfMonth < length -> year == other.year && month == other.month && dayOfMonth == other.dayOfMonth + 1
      other.month < 12 -> year == other.year && month == other.month + 1 && dayOfMonth == 1
      else -> year == other.year + 1 && month == 1 && dayOfMonth == 1
    }
  }

  companion object {
    private val FORMAT = Regex("""(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::\d{2})?)?Z?""")

    fun of(text: String): DateTime? {
      val match = FORMAT.matchEntire(text.trim()) ?: return null
      val (year, month, day, hour, minute) = match.destructured
      val m = month.toInt()
      if (m !in 1..12 || day.toInt() !in 1..daysIn(year.toInt(), m)) return null
      return DateTime(year.toInt(), m, day.toInt(), if (hour.isEmpty()) null else "$hour:00")
    }
  }
}

private fun daysIn(year: Int, month: Int): Int = when (month) {
  2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
  4, 6, 9, 11 -> 30
  else -> 31
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
