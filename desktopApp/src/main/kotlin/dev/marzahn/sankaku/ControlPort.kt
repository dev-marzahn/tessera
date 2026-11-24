package dev.marzahn.sankaku

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.marzahn.sankaku.log.RequestLog
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

internal class ControlPort private constructor(port: Int) {
  @Volatile private var window: Window? = null
  private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)

  init {
    server.createContext("/") { exchange ->
      runCatching { handle(exchange) }.onFailure { reply(exchange, 500, "${it::class.simpleName}: ${it.message}") }
    }
    server.start()
    println("Control port on http://127.0.0.1:${server.address.port}")
  }

  @Volatile var requestLog: RequestLog? = null

  fun attach(window: Window) {
    this.window = window
  }

  private fun requests(): String = requestLog?.entries?.value.orEmpty().withIndex().joinToString("\n") { (i, e) ->
    "$i ${e.method} ${e.host} ${e.pathTemplate} ${e.status ?: e.errorClass} ${e.durationMs}ms"
  }

  private fun handle(exchange: HttpExchange) {
    val query = parse(exchange.requestURI.rawQuery)
    val window = window ?: return reply(exchange, 503, "no window yet")
    fun int(name: String) = query[name]?.toInt() ?: error("missing $name")
    when (exchange.requestURI.path) {
      "/screenshot" -> {
        Thread.sleep(query["wait"]?.toLong() ?: 150)
        val png = onEdt { screenshot(window, full = query["full"] == "1") }
        exchange.responseHeaders.add("Content-Type", "image/png")
        exchange.sendResponseHeaders(200, png.size.toLong())
        exchange.responseBody.use { it.write(png) }
        return
      }
      "/requests" -> return reply(exchange, 200, requests())
      "/tree" -> return reply(exchange, 200, onEdt { tree(window) })
      "/components" -> return reply(exchange, 200, onEdt { components(window) })
      "/click" -> onEdt {
        click(window, int("x"), int("y"), button(query["button"]), query["count"]?.toInt() ?: 1, mods(query["mods"]))
      }
      "/move" -> onEdt { mouse(window, MouseEvent.MOUSE_MOVED, int("x"), int("y"), 0, 0, 0) }
      "/drag" -> onEdt { drag(window, int("x1"), int("y1"), int("x2"), int("y2")) }
      "/scroll" -> onEdt { scroll(window, int("x"), int("y"), int("dy")) }
      "/type" -> {
        val text = query["text"] ?: exchange.requestBody.readBytes().decodeToString()
        onEdt { type(window, text) }
      }
      "/key" -> onEdt { key(window, query["name"] ?: error("missing name"), mods(query["mods"])) }
      "/resize" -> onEdt { window.setSize(int("w") + window.insets.left + window.insets.right, int("h") + window.insets.top + window.insets.bottom) }
      else -> return reply(exchange, 404, "unknown call")
    }
    reply(exchange, 200, "ok")
  }

  private fun reply(exchange: HttpExchange, status: Int, body: String) {
    val bytes = (body + "\n").toByteArray()
    exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }

  private fun content(window: Window): Container = (window as? javax.swing.RootPaneContainer)?.contentPane ?: window

  private fun canvas(window: Window): Component = findLayer(window)?.canvas ?: error("no Skia layer")

  private fun target(window: Window, x: Int, y: Int): Pair<Component, java.awt.Point> =
    canvas(window) to java.awt.Point(x, y)

  private fun deliver(event: KeyEvent) {
    for (listener in event.component.keyListeners) {
      when (event.id) {
        KeyEvent.KEY_PRESSED -> listener.keyPressed(event)
        KeyEvent.KEY_TYPED -> listener.keyTyped(event)
        KeyEvent.KEY_RELEASED -> listener.keyReleased(event)
      }
    }
  }

  private fun mouse(window: Window, id: Int, x: Int, y: Int, modifiers: Int, clicks: Int, button: Int) {
    val (component, point) = target(window, x, y)
    component.dispatchEvent(
      MouseEvent(component, id, System.currentTimeMillis(), modifiers, point.x, point.y, clicks, false, button),
    )
  }

  private fun click(window: Window, x: Int, y: Int, button: Int, count: Int, modifiers: Int) {
    val down = modifiers or when (button) {
      MouseEvent.BUTTON2 -> InputEvent.BUTTON2_DOWN_MASK
      MouseEvent.BUTTON3 -> InputEvent.BUTTON3_DOWN_MASK
      else -> InputEvent.BUTTON1_DOWN_MASK
    }
    mouse(window, MouseEvent.MOUSE_MOVED, x, y, modifiers, 0, 0)
    for (n in 1..count) {
      mouse(window, MouseEvent.MOUSE_PRESSED, x, y, down, n, button)
      mouse(window, MouseEvent.MOUSE_RELEASED, x, y, modifiers, n, button)
      mouse(window, MouseEvent.MOUSE_CLICKED, x, y, modifiers, n, button)
    }
  }

  private fun drag(window: Window, x1: Int, y1: Int, x2: Int, y2: Int) {
    val down = InputEvent.BUTTON1_DOWN_MASK
    mouse(window, MouseEvent.MOUSE_MOVED, x1, y1, 0, 0, 0)
    mouse(window, MouseEvent.MOUSE_PRESSED, x1, y1, down, 1, MouseEvent.BUTTON1)
    val (component, start) = target(window, x1, y1)
    val steps = 12
    for (i in 1..steps) {
      val x = start.x + (x2 - x1) * i / steps
      val y = start.y + (y2 - y1) * i / steps
      component.dispatchEvent(MouseEvent(component, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), down, x, y, 0, false, 0))
    }
    val end = java.awt.Point(start.x + x2 - x1, start.y + y2 - y1)
    component.dispatchEvent(
      MouseEvent(component, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, end.x, end.y, 1, false, MouseEvent.BUTTON1),
    )
  }

  private fun scroll(window: Window, x: Int, y: Int, dy: Int) {
    val (component, point) = target(window, x, y)
    component.dispatchEvent(
      MouseWheelEvent(
        component, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, point.x, point.y, 0, false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, dy,
      ),
    )
  }

  private fun keyTarget(window: Window): Component = canvas(window)

  private fun type(window: Window, text: String) {
    val component = keyTarget(window)
    for (char in text) {
      val code = KeyEvent.getExtendedKeyCodeForChar(char.code)
      val now = System.currentTimeMillis()
      deliver(KeyEvent(component, KeyEvent.KEY_PRESSED, now, 0, code, char))
      deliver(KeyEvent(component, KeyEvent.KEY_TYPED, now, 0, KeyEvent.VK_UNDEFINED, char))
      deliver(KeyEvent(component, KeyEvent.KEY_RELEASED, now, 0, code, char))
    }
  }

  private fun key(window: Window, name: String, modifiers: Int) {
    val code = keyCode(name)
    val char = when (code) {
      KeyEvent.VK_ENTER -> '\n'
      KeyEvent.VK_TAB -> '\t'
      KeyEvent.VK_BACK_SPACE -> '\b'
      KeyEvent.VK_ESCAPE -> '\u001b'
      else -> if (name.length == 1) name[0] else KeyEvent.CHAR_UNDEFINED
    }
    val component = keyTarget(window)
    val now = System.currentTimeMillis()
    deliver(KeyEvent(component, KeyEvent.KEY_PRESSED, now, modifiers, code, char))
    if (char != KeyEvent.CHAR_UNDEFINED && modifiers == 0) {
      deliver(KeyEvent(component, KeyEvent.KEY_TYPED, now, 0, KeyEvent.VK_UNDEFINED, char))
    }
    deliver(KeyEvent(component, KeyEvent.KEY_RELEASED, now, modifiers, code, char))
  }

  private fun keyCode(name: String): Int {
    if (name.length == 1) return KeyEvent.getExtendedKeyCodeForChar(name[0].code)
    val field = when (name.lowercase()) {
      "enter", "return" -> "ENTER"
      "esc", "escape" -> "ESCAPE"
      "backspace" -> "BACK_SPACE"
      "pageup" -> "PAGE_UP"
      "pagedown" -> "PAGE_DOWN"
      else -> name.uppercase()
    }
    return KeyEvent::class.java.getField("VK_$field").getInt(null)
  }

  private fun button(name: String?): Int = when (name) {
    "middle" -> MouseEvent.BUTTON2
    "right" -> MouseEvent.BUTTON3
    else -> MouseEvent.BUTTON1
  }

  private fun mods(names: String?): Int = names.orEmpty().split(',').filter(String::isNotBlank).fold(0) { mask, name ->
    mask or when (name.trim().lowercase()) {
      "shift" -> InputEvent.SHIFT_DOWN_MASK
      "ctrl" -> InputEvent.CTRL_DOWN_MASK
      "alt", "option" -> InputEvent.ALT_DOWN_MASK
      "meta", "cmd" -> InputEvent.META_DOWN_MASK
      else -> error("unknown modifier $name")
    }
  }

  private fun screenshot(window: Window, full: Boolean): ByteArray {
    val layer = findLayer(window) ?: error("no Skia layer")
    val bitmap = layer.screenshot() ?: error("no frame drawn yet")
    val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("encoding failed")
    if (full || bitmap.width == layer.width) return png
    val source = ImageIO.read(ByteArrayInputStream(png))
    val scaled = BufferedImage(layer.width, layer.height, BufferedImage.TYPE_INT_ARGB)
    scaled.createGraphics().apply {
      setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      drawImage(source, 0, 0, layer.width, layer.height, null)
      dispose()
    }
    return ByteArrayOutputStream().also { ImageIO.write(scaled, "png", it) }.toByteArray()
  }

  private fun tree(window: Window): String {
    val origin = content(window).locationOnScreen
    val lines = mutableListOf<String>()
    fun walk(context: AccessibleContext, depth: Int) {
      if (depth > 60) return
      val role = context.accessibleRole?.toDisplayString(java.util.Locale.ROOT) ?: "?"
      val name = context.accessibleName.orEmpty()
      val bounds = runCatching {
        val component = context.accessibleComponent ?: return@runCatching ""
        val at = component.locationOnScreen ?: return@runCatching ""
        val size = component.size ?: return@runCatching ""
        " [${at.x - origin.x},${at.y - origin.y} ${size.width}x${size.height}]"
      }.getOrDefault("")
      if (name.isNotEmpty() || bounds.isNotEmpty()) lines += "  ".repeat(depth) + "$role \"$name\"$bounds"
      for (i in 0 until context.accessibleChildrenCount) {
        context.getAccessibleChild(i)?.accessibleContext?.let { walk(it, depth + 1) }
      }
    }
    val layer = findLayer(window)
    val root = (layer as? Accessible)?.accessibleContext ?: (window as Accessible).accessibleContext
    walk(root, 0)
    return lines.joinToString("\n")
  }

  private fun components(window: Window): String {
    val lines = mutableListOf("focus owner: ${window.mostRecentFocusOwner?.javaClass?.name}")
    fun walk(component: Component, depth: Int) {
      lines += "  ".repeat(depth) + "${component.javaClass.name} ${component.bounds.width}x${component.bounds.height} " +
        "keys=${component.keyListeners.size} mouse=${component.mouseListeners.size} focusable=${component.isFocusable}"
      if (component is Container) component.components.forEach { walk(it, depth + 1) }
    }
    walk(window, 0)
    return lines.joinToString("\n")
  }

  private fun findLayer(root: Component): SkiaLayer? {
    if (root is SkiaLayer) return root
    if (root is Container) root.components.forEach { child -> findLayer(child)?.let { return it } }
    return null
  }

  private fun <T> onEdt(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var result: Result<T>? = null
    SwingUtilities.invokeAndWait { result = runCatching(block) }
    return result!!.getOrThrow()
  }

  private fun parse(raw: String?): Map<String, String> = raw.orEmpty().split('&').filter(String::isNotEmpty).associate {
    val (key, value) = (it.split('=', limit = 2) + "").take(2)
    URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
  }

  companion object {
    fun fromProperty(): ControlPort? = System.getProperty("sankaku.debug.control")?.toIntOrNull()?.let(::ControlPort)
  }
}
