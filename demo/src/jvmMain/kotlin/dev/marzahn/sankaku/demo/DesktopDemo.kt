package dev.marzahn.sankaku.demo

import okio.Path.Companion.toOkioPath
import org.koin.core.module.Module
import java.nio.file.Files

fun desktopDemoModules(): List<Module> {
  val scratch = Files.createTempDirectory("tessera-demo-").toFile()
  Runtime.getRuntime().addShutdownHook(Thread { scratch.deleteRecursively() })
  return listOf(demoModule(scratch.toOkioPath()))
}
