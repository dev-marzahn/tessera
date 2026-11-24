package dev.marzahn.sankaku.queue

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@JvmInline
value class SessionId(val value: String) {
  companion object {
    val EARLIER = SessionId("earlier")

    fun random() = SessionId(Uuid.random().toString())
  }
}

class SessionClock private constructor(current: SessionId) {
  var current: SessionId = current
    private set

  fun startNew(): SessionId = SessionId.random().also { current = it }

  companion object {
    fun atLaunch(saved: SessionId?): SessionClock = SessionClock(saved ?: SessionId.random())

    fun of(session: SessionId): SessionClock = SessionClock(session)
  }
}
