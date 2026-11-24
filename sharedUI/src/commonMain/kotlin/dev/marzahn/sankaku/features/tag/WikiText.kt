package dev.marzahn.sankaku.features.tag

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.em

internal data class WikiTag(val shown: String, val searchName: String)

internal fun wikiText(dtext: String, linkColor: Color = Color.Unspecified, onTag: (WikiTag) -> Unit = {}): AnnotatedString {
  val links = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
  val open = mutableMapOf<String, Int>()
  var quoted = false
  var quoteStart: Int? = null
  return buildAnnotatedString {
    var lines = 0
    var blank = false

    fun endQuote() {
      quoteStart?.takeIf { it < length }?.let { addStyle(QuoteStyle, it, length) }
      quoteStart = null
      quoted = false
    }

    fun inline(part: String) {
      var at = 0
      for (match in INLINE.findAll(part)) {
        append(part.substring(at, match.range.first))
        at = match.range.last + 1
        val g = match.groups
        when {
          g[WIKI_TARGET] != null -> {
            val target = g[WIKI_TARGET]!!.value.trim()
            val tag = WikiTag(shown = target.replace('_', ' '), searchName = target.substringBefore('#').trim().lowercase().replace(' ', '_'))
            val label = g[WIKI_LABEL]?.value?.takeIf { it.isNotBlank() } ?: tag.shown
            withLink(LinkAnnotation.Clickable(tag.searchName, links) { onTag(tag) }) { append(label) }
          }
          g[NAMED_TEXT] != null -> {
            val url = g[NAMED_URL]!!.value
            if (url.startsWith("/")) append(g[NAMED_TEXT]!!.value) else withLink(LinkAnnotation.Url(url, links)) { append(g[NAMED_TEXT]!!.value) }
          }
          g[TAG_NAME] != null -> {
            val name = g[TAG_NAME]!!.value.lowercase()
            val closing = g[TAG_CLOSE]!!.value.isNotEmpty()
            when {
              name == QUOTE && !closing -> quoted = true
              name == QUOTE -> endQuote()
              else -> {
                val style = STYLES[name] ?: continue
                if (!closing) {
                  open.getOrPut(name) { length }
                } else {
                  open.remove(name)?.takeIf { it < length }?.let { addStyle(style, it, length) }
                }
              }
            }
          }
          g[BARE_URL] != null -> {
            val url = g[BARE_URL]!!.value.trimEnd('.', ',', ';', ':', '!', '?', ')')
            withLink(LinkAnnotation.Url(url, links)) { append(url) }
            append(g[BARE_URL]!!.value.substring(url.length))
          }
          g[SEARCH] != null -> append(g[SEARCH]!!.value)
        }
      }
      append(part.substring(at))
    }

    for (raw in dtext.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
      val line = raw.trim()
      if (line.isNotEmpty() && line.replace(ONLY_TAG, "").isBlank() && !line.contains("[[")) {
        inline(line)
        continue
      }
      if (line.isEmpty()) {
        blank = lines > 0
        continue
      }
      if (lines > 0) append(if (blank) "\n\n" else "\n")
      blank = false
      lines++
      if (quoted && quoteStart == null) quoteStart = length
      if (quoted) append(INDENT)
      val start = length
      val heading = HEADING.matchEntire(line)
      val bullet = BULLET.matchEntire(line)
      when {
        heading != null -> {
          inline(heading.groupValues[2])
          addStyle(headingStyle(heading.groupValues[1].toInt()), start, length)
        }
        bullet != null -> {
          if (bullet.groupValues[1].length > 1) append(INDENT)
          append("• ")
          inline(bullet.groupValues[2])
        }
        else -> inline(line)
      }
    }
    open.forEach { (name, from) -> if (from < length) addStyle(STYLES.getValue(name), from, length) }
    endQuote()
  }
}

private fun headingStyle(level: Int) = SpanStyle(
  fontWeight = FontWeight.SemiBold,
  fontSize = when (level) {
    in 1..3 -> 1.2.em
    4 -> 1.1.em
    else -> 1.em
  },
)

private const val INDENT = " "
private val QuoteStyle = SpanStyle(fontStyle = FontStyle.Italic)

private const val QUOTE = "quote"
private val STYLES = mapOf(
  "b" to SpanStyle(fontWeight = FontWeight.Bold),
  "i" to SpanStyle(fontStyle = FontStyle.Italic),
  "u" to SpanStyle(textDecoration = TextDecoration.Underline),
  "s" to SpanStyle(textDecoration = TextDecoration.LineThrough),
)

private val HEADING = Regex("""h([1-6])\.\s*(.*)""", RegexOption.IGNORE_CASE)
private val BULLET = Regex("""(\*+)\s+(.*)""")
private val ONLY_TAG = Regex("""\[(/?)([a-zA-Z]+)(?:=[^\]]*)?]""")

private const val WIKI_TARGET = 1
private const val WIKI_LABEL = 2
private const val NAMED_TEXT = 3
private const val NAMED_URL = 4
private const val TAG_CLOSE = 5
private const val TAG_NAME = 6
private const val BARE_URL = 7
private const val SEARCH = 8
private val INLINE = Regex(
  listOf(
    """\[\[([^\]|]+)(?:\|([^\]]*))?]]""",
    """"([^"]+)":\[?((?:https?://|/)[^\s\]]+)]?""",
    """\[(/?)([a-zA-Z]+)(?:=[^\]]*)?]""",
    """(https?://[^\s<>\[\]]+)""",
    """\{\{([^\}]*)\}\}""",
  ).joinToString("|"),
)
