package dev.marzahn.sankaku

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import kotlin.concurrent.thread

class SingleInstance private constructor(private val lock: FileLock, private val server: ServerSocket) {
  fun listen(onFocus: () -> Unit) {
    thread(isDaemon = true, name = "single-instance") {
      while (!server.isClosed) {
        runCatching {
          server.accept().use { socket ->
            if (socket.getInputStream().bufferedReader().readLine() == "focus") onFocus()
          }
        }
      }
    }
  }

  companion object {
    fun acquire(dataDir: File): SingleInstance? {
      dataDir.mkdirs()
      val file = RandomAccessFile(File(dataDir, "instance.lock"), "rw")
      val lock = file.channel.tryLock()
      if (lock == null) {
        val port = file.readLine()?.trim()?.toIntOrNull()
        file.close()
        port?.let { runCatching { Socket(InetAddress.getLoopbackAddress(), it).use { s -> s.getOutputStream().write("focus\n".toByteArray()) } } }
        return null
      }
      val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
      file.setLength(0)
      file.write("${server.localPort}\n".toByteArray())
      return SingleInstance(lock, server)
    }
  }
}
