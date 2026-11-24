package dev.marzahn.sankaku.cli

import java.io.File

fun loadEnv(dir: File = File(".")): Map<String, String> {
  val file = listOf(".env.local", ".env").map { File(dir, it) }.firstOrNull { it.exists() }
  val fromFile = file?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    ?.associate { it.substringBefore("=").trim() to it.substringAfter("=").trim().trim('"') }
    .orEmpty()
  return fromFile + System.getenv().filterKeys { it.startsWith("SANKAKU_") }
}
