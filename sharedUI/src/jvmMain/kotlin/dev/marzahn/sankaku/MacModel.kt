package dev.marzahn.sankaku

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.LongByReference

internal object MacModel {
  private interface Sysctl : Library {
    fun sysctlbyname(name: String, value: Pointer?, size: LongByReference, newValue: Pointer?, newSize: Long): Int
  }

  private val sysctl by lazy { runCatching { Native.load("System", Sysctl::class.java) }.getOrNull() }

  fun describe(): String? {
    val model = read("hw.model")
    val chip = read("machdep.cpu.brand_string")
    return when {
      model != null && chip != null -> "$model ($chip)"
      else -> model ?: chip
    }
  }

  private fun read(name: String): String? = runCatching {
    val library = sysctl ?: return null
    val size = LongByReference()
    if (library.sysctlbyname(name, null, size, null, 0) != 0 || size.value <= 0) return null
    val buffer = Memory(size.value)
    if (library.sysctlbyname(name, buffer, size, null, 0) != 0) return null
    buffer.getString(0).trim().ifEmpty { null }
  }.getOrNull()
}
