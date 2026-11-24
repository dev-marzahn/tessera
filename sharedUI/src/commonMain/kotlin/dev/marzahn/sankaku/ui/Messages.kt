package dev.marzahn.sankaku.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class Message(
  val text: String,
  val actionLabel: String? = null,
  val isError: Boolean = false,
  val short: Boolean = false,
  val until: (suspend () -> Unit)? = null,
  val onAction: () -> Unit = {},
)

class Messages {
  private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val messages: SharedFlow<Message> = _messages.asSharedFlow()

  fun show(message: Message) {
    _messages.tryEmit(message)
  }

  fun show(text: String) = show(Message(text))

  fun showError(text: String) = show(Message(text, isError = true))
}
