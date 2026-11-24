package dev.marzahn.sankaku

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

internal object MacMemory {
  @Suppress("FunctionName")
  private interface Mach : Library {
    fun task_info(task: Int, flavor: Int, info: Pointer, count: IntByReference): Int
  }

  private const val TASK_VM_INFO = 22

  private const val PHYS_FOOTPRINT_OFFSET = 144L

  private const val INFO_INTS = 128

  private const val REV1_INTS = ((PHYS_FOOTPRINT_OFFSET + 8) / 4).toInt()

  private val mach by lazy {
    runCatching {
      val system = NativeLibrary.getInstance("System")
      val task = system.getGlobalVariableAddress("mach_task_self_").getInt(0)
      Native.load("System", Mach::class.java) to task
    }.getOrNull()
  }

  fun physFootprint(): Long? {
    val (library, task) = mach ?: return null
    val info = Memory(INFO_INTS * 4L)
    val count = IntByReference(INFO_INTS)
    if (library.task_info(task, TASK_VM_INFO, info, count) != 0 || count.value < REV1_INTS) return null
    return info.getLong(PHYS_FOOTPRINT_OFFSET)
  }
}
