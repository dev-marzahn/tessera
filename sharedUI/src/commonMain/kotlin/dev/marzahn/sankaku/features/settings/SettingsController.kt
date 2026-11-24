package dev.marzahn.sankaku.features.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsController {
  private val _isOpen = MutableStateFlow(false)
  val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

  fun open() {
    _isOpen.value = true
  }

  fun close() {
    _isOpen.value = false
  }
}
