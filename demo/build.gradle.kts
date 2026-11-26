import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.time.Duration

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
  applyDefaultHierarchyTemplate {
    common {
      group("jvmAndAndroid") {
        withCompilations { it.platformType in setOf(KotlinPlatformType.jvm, KotlinPlatformType.androidJvm) }
      }
    }
  }

  jvm()

  android {
    namespace = "dev.marzahn.sankaku.demo"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
  }

  compilerOptions {
    allWarningsAsErrors = true
  }

  sourceSets {
    commonMain.dependencies {
      implementation(projects.sharedUI)
      implementation(libs.ktor.client.mock)
      implementation(libs.okio)
    }
    getByName("jvmAndAndroidMain") {
      resources.srcDir("src/media")
    }
    androidMain.dependencies {
      implementation(libs.androidx.media3.exoplayer)
    }
    jvmTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.androidx.paging.testing)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.java.keyring)
      implementation(libs.androidx.navigation3.runtime)
      implementation(libs.androidx.lifecycle.viewmodelCompose)
    }
  }
}

tasks.named<Test>("jvmTest") {
  val dataDir = layout.buildDirectory.dir("test-app-data").get().asFile
  systemProperty("sankaku.debug.dataDir", dataDir.path)
  systemProperty("sankaku.debug.safeOnly", "true")
  timeout.set(Duration.ofMinutes(3))
  testLogging { events("started", "failed") }
  doFirst { dataDir.deleteRecursively() }
}
