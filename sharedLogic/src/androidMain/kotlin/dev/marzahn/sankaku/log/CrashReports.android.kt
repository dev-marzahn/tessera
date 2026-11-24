package dev.marzahn.sankaku.log

internal actual val Throwable.typeName: String get() = javaClass.name

internal actual fun Throwable.frames(): List<String> =
  stackTrace.map { "${it.className}.${it.methodName}(${it.fileName ?: "Unknown Source"}:${it.lineNumber})" }

actual fun CrashReports.installUncaughtHandler(closesApp: () -> Boolean) {
  val previous = Thread.getDefaultUncaughtExceptionHandler()
  val lock = Any()
  Thread.setDefaultUncaughtExceptionHandler { thread, error ->
    try {
      synchronized(lock) { if (runCatching(closesApp).getOrDefault(true)) record(error) }
    } finally {
      if (previous != null) {
        previous.uncaughtException(thread, error)
      } else {
        System.err.print("Exception in thread \"${thread.name}\" ")
        error.printStackTrace(System.err)
      }
    }
  }
}
