package dev.marzahn.sankaku

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

private const val BUNDLE_ID = "dev.marzahn.tessera"

internal object LaunchFocus {
  private var previous: Pointer? = null

  private val enabled =
    System.getProperty("os.name").startsWith("Mac") && System.getenv("__CFBundleIdentifier") != BUNDLE_ID

  private val objc by lazy { NativeLibrary.getInstance("objc") }

  private fun send(receiver: Pointer?, selector: String, vararg args: Any?): Pointer? {
    val sel = objc.getFunction("sel_registerName").invokePointer(arrayOf(selector))
    return objc.getFunction("objc_msgSend").invokePointer(arrayOf(receiver, sel, *args))
  }

  private fun cls(name: String): Pointer? = objc.getFunction("objc_getClass").invokePointer(arrayOf(name))

  fun remember() {
    if (!enabled) return
    previous = runCatching {
      val workspace = send(cls("NSWorkspace"), "sharedWorkspace")
      send(send(workspace, "frontmostApplication"), "retain")
    }.getOrNull()
  }

  fun yieldFocus() {
    val app = previous ?: return
    previous = null
    runCatching {
      send(app, "activateWithOptions:", 0L)
      send(app, "release")
    }
  }
}
