package dev.marzahn.sankaku.features.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class OpenSourceLibrary(val name: String, val id: String, val version: String?, val licences: List<Licence>)

internal data class Licence(val name: String, val text: String?)

internal expect val licencesTarget: String

internal const val LICENCES_RESOURCE = "files/aboutlibraries.json"

internal fun openSourceLibraries(json: String, target: String): List<OpenSourceLibrary> {
  val export = LicencesJson.decodeFromString<Export>(json)
  return export.libraries
    .filter { target in it.targets }
    .map { library ->
      val licences = library.licenses.map { id ->
        val licence = export.licenses[id]
        Licence(name = licence?.name ?: id, text = licence?.content?.let(::spdxText))
      }
      OpenSourceLibrary(
        name = library.name.ifBlank { library.uniqueId },
        id = library.uniqueId,
        version = library.artifactVersion,
        licences = licences.filter { it.text != null }.ifEmpty { licences },
      )
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, OpenSourceLibrary::name).thenBy(OpenSourceLibrary::id))
}

internal fun spdxText(template: String): String =
  SpdxVar.replace(template) { match -> match.groupValues[1].trim() }

private val SpdxVar = Regex("""<<var;[^>]*?original=(.*?);match=.*?>>""")

private val LicencesJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class Export(val libraries: List<ExportLibrary> = emptyList(), val licenses: Map<String, ExportLicence> = emptyMap())

@Serializable
private data class ExportLibrary(
  val uniqueId: String,
  val name: String = "",
  val artifactVersion: String? = null,
  val licenses: List<String> = emptyList(),
  val targets: List<String> = emptyList(),
)

@Serializable
private data class ExportLicence(val name: String, val content: String? = null)
