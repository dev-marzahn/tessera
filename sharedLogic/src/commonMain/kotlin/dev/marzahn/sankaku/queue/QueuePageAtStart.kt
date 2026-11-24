package dev.marzahn.sankaku.queue

object QueuePageAtStart {
  fun opens(kinds: List<QueueKind>): Boolean =
    kinds.any { it.isKept } || kinds.count { it == QueueKind.Source } > 1
}
