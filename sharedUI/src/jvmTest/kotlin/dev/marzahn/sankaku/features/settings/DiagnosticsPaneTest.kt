package dev.marzahn.sankaku.features.settings

import androidx.compose.foundation.layout.Column
import app.cash.turbine.test
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.marzahn.sankaku.NoAppForLink
import dev.marzahn.sankaku.OpenUrlResult
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.TextFiles
import dev.marzahn.sankaku.log.AppEnvironment
import dev.marzahn.sankaku.log.Diagnostics
import dev.marzahn.sankaku.log.RequestLog
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class DiagnosticsPaneTest {
  private val noFiles = TextFiles { InMemoryTextFile() }

  private val clock = object : Clock {
    override fun now(): Instant = Instant.parse("2026-10-29T12:00:00Z")
  }

  private val environment = AppEnvironment("1.2.3", "macOS", "macOS 26.1", "Mac15,12 (Apple M3)")

  private class FakeClipboard : Clipboard {
    var entry: ClipEntry? = null
    override suspend fun getClipEntry(): ClipEntry? = entry
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
      entry = clipEntry
    }
    override val nativeClipboard: NativeClipboard = java.awt.datatransfer.Clipboard("test")
  }

  @Test
  fun showsTheLogNewestFirstAndCopiesAndReports() = runComposeUiTest {
    val log = RequestLog(clock)
    log.record("GET", "sankakuapi.com", "/users/me", 200, 12, null)
    log.record("POST", "sankakuapi.com", "/posts/abc123/favorite", 401, 40, null)
    log.record("GET", "v.sankakucomplex.com", "/data/sample/01/23/sample-0123456789abcdef0123456789abcdef.jpg", null, 30_000, "SocketTimeoutException")
    val diagnostics = Diagnostics(log, environment)
    val messages = Messages()
    val shown = mutableListOf<String>()
    val opened = mutableListOf<String>()
    val clipboard = FakeClipboard()
    setContent {
      SankakuTheme {
        LaunchedEffect(messages) { messages.messages.collect { shown += it.text } }
        CompositionLocalProvider(LocalClipboard provides clipboard) {
          Column {
            DiagnosticsSection(diagnostics, messages, files = noFiles, open = {
              opened += it
              OpenUrlResult.Opened
            })
          }
        }
      }
    }
    waitForIdle()

    val tops = listOf("/data/sample/{md5}", "/posts/{id}/favorite", "/users/me").map {
      onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top
    }
    assertEquals(tops.sorted(), tops)
    onNodeWithText("SocketTimeoutException · 30000 ms").assertExists()
    onNodeWithText("401 · 40 ms").assertExists()
    onNodeWithText("200 · 12 ms").assertExists()
    assertEquals(0, onAllNodesWithText("abc123", substring = true).fetchSemanticsNodes().size)

    onNodeWithText("Copy log").performClick()
    waitUntil(timeoutMillis = 5_000) { shown.isNotEmpty() }
    assertEquals(listOf("Log copied"), shown)
    val copied = (clipboard.entry!!.nativeClipEntry as Transferable).getTransferData(DataFlavor.stringFlavor)
    assertEquals(diagnostics.copyText(), copied)

    onNode(hasText("Report a bug")).performClick()
    waitForIdle()
    assertEquals(listOf(diagnostics.bugReportUrl()), opened)
    assertEquals(listOf("Log copied"), shown)
  }

  @Test
  fun reportABugSaysSoWhenNoAppCanOpenTheForm() = runTest {
    val messages = Messages()
    messages.messages.test {
      reportBug(Diagnostics(RequestLog(clock), environment), messages, open = { OpenUrlResult.Failed })
      assertEquals(Message(NoAppForLink, isError = true), awaitItem())
    }
  }

  @Test
  fun anEmptyLogSaysSo() = runComposeUiTest {
    val log = RequestLog(clock)
    setContent {
      SankakuTheme {
        Column { DiagnosticsSection(Diagnostics(log, environment), Messages(), files = noFiles, open = { OpenUrlResult.Opened }) }
      }
    }
    onNodeWithText("No requests yet").assertExists()
  }

  @Test
  fun exportQueueIsOfferedWhereThereIsAShareSheetAndRunsTheExport() = runComposeUiTest {
    var exports = 0
    setContent {
      SankakuTheme {
        Column {
          DiagnosticsSection(Diagnostics(RequestLog(clock), environment), Messages(), files = noFiles, open = { OpenUrlResult.Opened }, exportQueue = { exports++ })
        }
      }
    }
    onNodeWithText(ExportQueueHelp).assertExists()
    onNodeWithText("Export queue").performClick()
    waitUntil(timeoutMillis = 5_000) { exports == 1 }
  }

  @Test
  fun withoutAShareSheetThereIsNoExport() = runComposeUiTest {
    setContent {
      SankakuTheme {
        Column {
          DiagnosticsSection(Diagnostics(RequestLog(clock), environment), Messages(), files = noFiles, open = { OpenUrlResult.Opened }, exportQueue = null)
        }
      }
    }
    onNodeWithText("Export queue").assertDoesNotExist()
  }

  @Test
  fun theExportHoldsBothFilesAsSavedAndSurvivesABrokenOne() = runTest {
    val files = mapOf("tabs.json" to """{"version":2,"tabs":[]}""", "seen.json" to """{"version":1,"posts":[""")
    var shared: Triple<String, String, String>? = null
    shareQueue(Messages(), read = files::get, share = { name, type, text -> shared = Triple(name, type, text) }, now = clock::now)

    val (name, type, text) = shared!!
    assertEquals(QueueExportName, name)
    assertEquals("application/json", type)
    val export = Json.parseToJsonElement(text).jsonObject
    assertEquals("2026-10-29T12:00:00Z", export.getValue("exportedAt").jsonPrimitive.content)
    assertEquals(Json.parseToJsonElement(files.getValue("tabs.json")), export.getValue("tabs"))
    assertEquals(files.getValue("seen.json"), export.getValue("seen").jsonPrimitive.content)
    assertEquals(JsonNull, Json.parseToJsonElement(queueExport(null, null, clock.now())).jsonObject.getValue("seen"))
  }

  @Test
  fun anExportThatCantBeWrittenSaysSoInsteadOfCrashing() = runTest {
    val messages = Messages()
    messages.messages.test {
      shareQueue(messages, read = { null }, share = { _, _, _ -> throw java.io.IOException("No space left on device") })
      assertEquals(Message(ExportQueueFailed, isError = true), awaitItem())
    }
  }

  @Test
  fun timesAreOnTheDevicesClock() {
    val log = RequestLog(clock)
    log.record("GET", "sankakuapi.com", "/users/me", 200, 12, null)
    val entry = log.entries.value.single()
    assertEquals("12:00:00", entry.localTime(TimeZone.UTC))
    assertEquals("13:00:00", entry.localTime(TimeZone.of("Europe/Berlin")))
    assertTrue(entry.copy(status = null).failed)
    assertTrue(entry.copy(status = 503).failed)
  }
}
