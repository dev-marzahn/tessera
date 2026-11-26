package dev.marzahn.sankaku

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue

class ExportsProviderTest {
  private val cacheDir = File("cache")

  private val sharedFolders: List<File> =
    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/exports.xml"))
      .getElementsByTagName("cache-path")
      .let { paths -> (0 until paths.length).map { File(cacheDir, (paths.item(it) as Element).getAttribute("path")) } }

  @Test
  fun theProviderSharesTheExportsFolder() {
    val exports = ExportsFolder.inCache(cacheDir).dir
    assertTrue(sharedFolders.any { exports.startsWith(it) }, "exports.xml shares $sharedFolders")
  }
}
