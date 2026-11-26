plugins {
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidMultiplatformLibrary) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.kotlinJvm) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinx.serialization) apply false
  alias(libs.plugins.koin.compiler) apply false
  alias(libs.plugins.aboutlibraries) apply false

  alias(libs.plugins.ben.manes.versions)
  alias(libs.plugins.version.catalog.update)
}

private val preRelease = Regex("""(?i)[.-](alpha|beta|rc|cr|m|eap|dev|snapshot|compat)[.\d-]*""")

private fun isPreRelease(version: String) = preRelease.containsMatchIn(version)

private fun minorOf(version: String) = version.split('.').take(2).joinToString(".")

private val composeVersion = libs.versions.composeMultiplatform.get()

private fun skipsUpdate(candidate: ModuleComponentIdentifier, currentVersion: String): Boolean {
  val isMaterial3 = candidate.group == "org.jetbrains.compose.material3" && candidate.module == "material3"
  return if (isMaterial3) {
    minorOf(candidate.version) != minorOf(composeVersion)
  } else {
    isPreRelease(candidate.version) && !isPreRelease(currentVersion)
  }
}

versionCatalogUpdate {
  sortByKey = false
  versionSelector(
    object : nl.littlerobots.vcu.plugin.resolver.ModuleVersionSelector {
      override fun select(candidate: nl.littlerobots.vcu.plugin.resolver.ModuleVersionCandidate) =
        !skipsUpdate(candidate.candidate, candidate.currentVersion)
    },
  )
}

tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates") {
  rejectVersionIf { skipsUpdate(candidate, currentVersion) }
}
val releaseVersion = libs.versions.tessera.get()
val releaseDir = layout.buildDirectory.dir("release")

fun missingProperties(names: List<String>) = names.filter { providers.gradleProperty(it).orNull.isNullOrBlank() }

fun registerPropertyCheck(name: String, problem: Provider<String>) = tasks.register(name) {
  doLast {
    check(problem.get().isEmpty()) {
      "release: ${problem.get()} in ~/.gradle/gradle.properties (see the README's Releases section)"
    }
  }
}

fun registerReleaseCopy(name: String, files: Map<String, String>, keep: List<String>, configure: Sync.() -> Unit) =
  tasks.register<Sync>(name) {
    group = "distribution"
    configure()
    files.forEach { (release, built) -> from(layout.projectDirectory.file(built)) { rename { release } } }
    into(releaseDir)
    preserve { include(keep) }
    val out = releaseDir
    val expected = files.keys
    doLast {
      val missing = expected.filterNot { out.get().file(it).asFile.exists() }
      check(missing.isEmpty()) { "release: ${missing.joinToString()} not built" }
    }
  }

val androidSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

fun androidSigningEnv(name: String) = "TESSERA_SIGNING_" + name.replace(Regex("([A-Z])"), "_$1").uppercase()

val checkAndroidReleaseProperties = registerPropertyCheck(
  "checkAndroidReleaseProperties",
  providers.provider {
    androidSigning.filter {
      providers.gradleProperty("tessera.signing.$it").orNull.isNullOrBlank() &&
        providers.environmentVariable(androidSigningEnv(it)).orNull.isNullOrBlank()
    }.let { missing ->
      if (missing.isEmpty()) "" else "set ${missing.joinToString { "tessera.signing.$it (or ${androidSigningEnv(it)})" }}"
    }
  },
)

val macSigning = listOf("tessera.mac.signingIdentity", "tessera.mac.notaryProfile")
val checkMacReleaseProperties = registerPropertyCheck(
  "checkMacReleaseProperties",
  providers.provider {
    missingProperties(macSigning).singleOrNull()?.let { "set $it too, or neither Mac property" } ?: ""
  },
)

fun projectsReachedFrom(root: ResolvedComponentResult): Set<String> {
  val reached = mutableSetOf<ResolvedComponentResult>()
  val next = ArrayDeque(listOf(root))
  while (next.isNotEmpty()) {
    val component = next.removeFirst()
    if (reached.add(component)) next += component.dependencies.filterIsInstance<ResolvedDependencyResult>().map { it.selected }
  }
  return reached.mapNotNull { (it.id as? ProjectComponentIdentifier)?.projectPath }.toSet()
}

fun registerNoDemoCheck(app: Project, name: String, classpath: String, shipped: String): TaskProvider<Task> {
  val demo = projects.demo.path
  val guard = app.tasks.register(name) {
    group = "verification"
    description = "Fails if the $shipped's classpath contains $demo."
    val shippedProjects = app.configurations.named(classpath).flatMap { files ->
      files.incoming.resolutionResult.rootComponent.map { root -> projectsReachedFrom(root) }
    }
    doLast { check(demo !in shippedProjects.get()) { "$demo is on the $shipped's classpath" } }
  }
  app.tasks.named { it == "check" }.configureEach { dependsOn(guard) }
  return guard
}

val releaseApkHasNoDemo =
  registerNoDemoCheck(project(projects.androidApp.path), "checkReleaseApkHasNoDemo", "releaseRuntimeClasspath", "release APK")

val dmgHasNoDemo = registerNoDemoCheck(project(projects.desktopApp.path), "checkDmgHasNoDemo", "runtimeClasspath", "DMG")

val releaseApk = registerReleaseCopy(
  "releaseApk",
  mapOf(
    "Tessera-$releaseVersion.apk" to "androidApp/build/outputs/apk/release/androidApp-release.apk",
    "Tessera-$releaseVersion-mapping.txt" to "androidApp/build/outputs/mapping/release/mapping.txt",
  ),
  keep = listOf("*.dmg"),
) {
  description = "Builds the signed, R8-minified release APK and its mapping into build/release/."
  dependsOn(checkAndroidReleaseProperties, releaseApkHasNoDemo, ":androidApp:assembleRelease")
}

val releaseMac = registerReleaseCopy(
  "releaseMac",
  mapOf("Tessera-$releaseVersion.dmg" to "desktopApp/build/release-dmg/Tessera-$releaseVersion.dmg"),
  keep = listOf("*.apk", "*-mapping.txt"),
) {
  description = "Builds the release DMG (notarized when configured) into build/release/."
  dependsOn(checkMacReleaseProperties, dmgHasNoDemo, ":desktopApp:releaseDmg")
  mustRunAfter(releaseApk)
}

tasks.register("release") {
  group = "distribution"
  description = "Builds the signed release APK and the DMG (notarized when configured) into build/release/."
  dependsOn(releaseApk, releaseMac)
}
