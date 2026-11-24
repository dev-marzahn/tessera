package dev.marzahn.sankaku.api.dto

object TagNames {
  fun shown(name: String, key: String?): String =
    if (key == null || name.removePrefix("-").equals(key.removePrefix("-"), ignoreCase = true)) name.replace('_', ' ') else name

  fun ofKey(key: String): String {
    val spaced = key.replace('_', ' ')
    return buildString(spaced.length) {
      spaced.forEachIndexed { i, c -> append(if (i == 0 || spaced[i - 1] == ' ' || spaced[i - 1] == '(') c.uppercaseChar() else c) }
    }
  }
}
