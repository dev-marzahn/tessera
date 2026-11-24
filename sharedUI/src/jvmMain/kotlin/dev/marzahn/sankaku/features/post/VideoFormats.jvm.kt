package dev.marzahn.sankaku.features.post

internal actual fun playsVideoFile(source: String): Boolean = desktopPlaysVideoFile(source, isMac = IS_MAC)

internal fun desktopPlaysVideoFile(source: String, isMac: Boolean): Boolean = !isMac || avPlayerOpens(source)

internal fun avPlayerOpens(source: String): Boolean = !source.substringAfterLast('/').endsWith(".webm", ignoreCase = true)

private val IS_MAC = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)
