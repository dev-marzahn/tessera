import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.koin.compiler)
  alias(libs.plugins.aboutlibraries)
}

koinCompiler {
  strictSafety = true
}

val licencesFile = layout.buildDirectory.file("generated/aboutLibraries/aboutlibraries.json")
aboutLibraries {
  offlineMode = true
  collect {
    configPath = file("licences")
    includeTargets = true
  }
  export {
    outputFile = licencesFile
    excludeFields.addAll("developers", "funding", "organization", "scm")
  }
  library {
    duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
    mergePlatformArtifacts = true
  }
}

val commonResourcesDir = layout.buildDirectory.dir("generated/commonResources")
val commonResources = tasks.register<Sync>("commonResources") {
  from("src/commonMain/composeResources")
  from(tasks.named("exportLibraryDefinitions")) {
    include("aboutlibraries.json")
    into("files")
  }
  into(commonResourcesDir)
}

compose.resources {
  packageOfResClass = "dev.marzahn.sankaku.resources"
  customDirectory(
    sourceSetName = "commonMain",
    directoryProvider = commonResources.map { commonResourcesDir.get() },
  )
}

kotlin {
  jvm()

  android {
    namespace = "dev.marzahn.sankaku.ui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
    androidResources {
      enable = true
    }
    withHostTest {
      isReturnDefaultValues = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(projects.sharedLogic)
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.uiToolingPreview)
      implementation(libs.compose.material3)
      implementation(libs.jetbrains.material3.adaptiveNavigation3)
      implementation(libs.androidx.navigation3.runtime)
      implementation(libs.jetbrains.navigation3.ui)
      implementation(libs.androidx.lifecycle.viewmodelCompose)
      implementation(libs.androidx.lifecycle.runtimeCompose)
      implementation(libs.jetbrains.lifecycle.viewmodelNavigation3)
      api(libs.koin.core)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.compose.navigation3)
      implementation(libs.androidx.paging.compose)
      implementation(libs.coil.compose)
      implementation(libs.coil.network.ktor3)
      implementation(libs.material.kolor)
      implementation(libs.zoomimage.compose.coil3)
      implementation(libs.kotlinx.datetime)
    }
    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.composemediaplayer)
      implementation(libs.kotlinx.coroutines.swing)
      implementation(libs.java.keyring)
      implementation(libs.jna)
      implementation(libs.jetbrains.navigationevent.compose)
      runtimeOnly(libs.slf4j.nop)
    }
    androidMain.dependencies {
      implementation(libs.kotlinx.coroutines.android)
      api(libs.koin.android)
      implementation(libs.androidx.activity.compose)
      implementation(libs.coil.gif)
      implementation(libs.androidx.media3.exoplayer)
    }
    jvmTest.dependencies {
      implementation(libs.compose.uiTest)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.turbine)
      implementation(libs.ktor.client.mock)
    }
  }
}

tasks.withType<Test>().configureEach {
  maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
  systemProperty("apple.awt.UIElement", "true")
}
