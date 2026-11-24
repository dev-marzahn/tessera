package dev.marzahn.sankaku.api

import io.ktor.http.Url
import io.ktor.util.AttributeKey

const val CHROME_MAJOR_VERSION = 158

enum class BrowserPlatform(
  internal val userAgentPlatform: String,
  internal val mobile: Boolean,
  internal val clientHintPlatform: String,
) {
  Android("Linux; Android 10; K", mobile = true, clientHintPlatform = "Android"),
  Mac("Macintosh; Intel Mac OS X 10_15_7", mobile = false, clientHintPlatform = "macOS"),

  AndroidDesktopSite("X11; Linux x86_64", mobile = false, clientHintPlatform = "Linux"),
  ;

  val forNumericIds: BrowserPlatform? get() = if (this == AndroidDesktopSite) Android else null
}

private val NUMERIC = Regex("[0-9]+")

private val NUMERIC_TOKEN = Regex("(?:^|\\s)(?:id|pool|parent|recommended_for_post):[0-9]+(?:\\s|$)")

internal fun aboutNumericId(url: Url): Boolean {
  if (url.host != API_HOST) return false
  val segments = url.segments
  val inPath = segments.zipWithNext().any { (resource, id) -> resource in ID_RESOURCES && NUMERIC.matches(id) }
  return inPath || url.parameters.getAll("tags").orEmpty().any { NUMERIC_TOKEN.containsMatchIn(it) }
}

private val ID_RESOURCES = setOf("posts", "post", "pools")

internal val AboutId = AttributeKey<String>("AboutId")

internal fun aboutNumericId(url: Url, aboutId: String?): Boolean =
  aboutNumericId(url) || aboutId != null && NUMERIC.matches(aboutId)

internal expect val currentBrowserPlatform: BrowserPlatform

class BrowserFingerprint(val platform: BrowserPlatform, val majorVersion: Int = CHROME_MAJOR_VERSION) {
  internal val forNumericIds: BrowserFingerprint? by lazy { platform.forNumericIds?.let { BrowserFingerprint(it, majorVersion) } }

  val userAgent: String =
    "Mozilla/5.0 (${platform.userAgentPlatform}) AppleWebKit/537.36 (KHTML, like Gecko) " +
      "Chrome/$majorVersion.0.0.0 ${if (platform.mobile) "Mobile " else ""}Safari/537.36"

  val clientHints: Map<String, String> = mapOf(
    "sec-ch-ua" to brandList(majorVersion),
    "sec-ch-ua-mobile" to if (platform.mobile) "?1" else "?0",
    "sec-ch-ua-platform" to "\"${platform.clientHintPlatform}\"",
  )

  companion object {
    private val greaseChars = listOf(" ", "(", ":", "-", ".", "/", ")", ";", "=", "?", "_")
    private val greaseVersions = listOf("8", "99", "24")
    private val orders = listOf(
      listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0),
    )

    internal fun brandList(majorVersion: Int): String {
      val seed = majorVersion
      val grease = "Not${greaseChars[seed % greaseChars.size]}A${greaseChars[(seed + 1) % greaseChars.size]}Brand"
      val brands = listOf(
        grease to greaseVersions[seed % greaseVersions.size],
        "Chromium" to "$majorVersion",
        "Google Chrome" to "$majorVersion",
      )
      val order = orders[seed % orders.size]
      val shuffled = arrayOfNulls<Pair<String, String>>(brands.size)
      brands.forEachIndexed { i, brand -> shuffled[order[i]] = brand }
      return shuffled.filterNotNull().joinToString(", ") { (name, version) -> "\"$name\";v=\"$version\"" }
    }

    val Current: BrowserFingerprint = BrowserFingerprint(currentBrowserPlatform)
  }
}
