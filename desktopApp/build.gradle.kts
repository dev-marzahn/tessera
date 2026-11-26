import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import javax.inject.Inject

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

val demoLauncher by sourceSets.creating {
  compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}

dependencies {
  implementation(projects.sharedUI)
  implementation(compose.desktop.currentOs)
  implementation(libs.kotlinx.coroutines.swing)
  implementation(libs.jna)
  demoLauncher.implementationConfigurationName(projects.demo)
  testImplementation(libs.kotlin.test)
}

sourceSets.test {
  compileClasspath += demoLauncher.output
  runtimeClasspath += demoLauncher.output + demoLauncher.runtimeClasspath
}

tasks.test {
  val dataDir = layout.buildDirectory.dir("test-app-data").get().asFile
  systemProperty("sankaku.debug.dataDir", dataDir.path)
  doFirst { dataDir.deleteRecursively() }
}

val runDemo by tasks.registering(JavaExec::class) {
  group = "compose desktop"
  description = "Runs the app in demo mode: bundled demo posts, no Sankaku connection, nothing stored."
  javaLauncher = tasks.named<JavaExec>("run").flatMap { it.javaLauncher }
  val appJvmArgs = tasks.named<JavaExec>("run").map { app ->
    app.jvmArgs.orEmpty() + app.systemProperties.map { (key, value) -> "-D$key=$value" }
  }
  dependsOn("prepareAppResources")
  mainClass = "dev.marzahn.sankaku.DemoMainKt"
  classpath(sourceSets.main.map { it.runtimeClasspath }, demoLauncher.output, demoLauncher.runtimeClasspath)
  jvmArgumentProviders += CommandLineArgumentProvider { appJvmArgs.get() }
}

tasks.withType<JavaExec>().configureEach {
  val forwarded = providers.systemPropertiesPrefixedBy("sankaku.")
  jvmArgumentProviders += CommandLineArgumentProvider {
    forwarded.get().map { (key, value) -> "-D$key=$value" }
  }
}

val signingIdentity = providers.gradleProperty("tessera.mac.signingIdentity").filter { it.isNotBlank() }
val notaryProfile = providers.gradleProperty("tessera.mac.notaryProfile").filter { it.isNotBlank() }

compose.desktop {
  application {
    mainClass = "dev.marzahn.sankaku.MainKt"
    jvmArgs += "-Xmx512m"

    nativeDistributions {
      targetFormats(TargetFormat.Dmg)
      packageName = "Tessera"
      packageVersion = libs.versions.tessera.get()
      modules("java.instrument", "java.management", "java.naming", "jdk.crypto.ec", "jdk.unsupported")
      macOS {
        bundleID = "dev.marzahn.tessera"
        iconFile = project.file("src/main/resources/icon.icns")
        signing {
          sign = signingIdentity.isPresent
          identity = signingIdentity
        }
      }
    }
  }
}

abstract class ReleaseDmg @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
  @get:InputDirectory
  abstract val dmgDir: DirectoryProperty

  @get:Input
  abstract val appVersion: Property<String>

  @get:Input
  @get:Optional
  abstract val keychainProfile: Property<String>

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @TaskAction
  fun run() {
    val dmg = dmgDir.get().asFile.resolve("Tessera-${appVersion.get()}.dmg")
    val out = outputDir.get().asFile.resolve(dmg.name)
    outputDir.get().asFile.listFiles()!!.forEach { it.delete() }
    dmg.copyTo(out)
    val profile = keychainProfile.orNull
    if (profile == null) {
      logger.warn("${dmg.name} is not notarized: set tessera.mac.signingIdentity and tessera.mac.notaryProfile to notarize it.")
      return
    }
    exec.exec { commandLine("xcrun", "notarytool", "submit", out, "--keychain-profile", profile, "--wait") }
    exec.exec { commandLine("xcrun", "stapler", "staple", out) }
    exec.exec { commandLine("xcrun", "stapler", "validate", out) }
    exec.exec { commandLine("spctl", "--assess", "--type", "open", "--context", "context:primary-signature", "-v", out) }
  }
}

tasks.register<ReleaseDmg>("releaseDmg") {
  description = "The release DMG: notarized when tessera.mac.signingIdentity and tessera.mac.notaryProfile are set."
  dependsOn("packageDmg")
  dmgDir = layout.buildDirectory.dir("compose/binaries/main/dmg")
  appVersion = libs.versions.tessera
  keychainProfile = signingIdentity.flatMap { notaryProfile }
  outputDir = layout.buildDirectory.dir("release-dmg")
}

tasks.matching { it.name == "packageDmg" }.configureEach {
  mustRunAfter(":checkAndroidReleaseProperties", ":checkMacReleaseProperties")
}
