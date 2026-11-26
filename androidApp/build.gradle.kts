plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeCompiler)
}

val appVersion = libs.versions.tessera.get()
val appVersionCode = appVersion.split('.').map { it.toIntOrNull() ?: -1 }.let { parts ->
  require(parts.size == 3 && parts[0] >= 0 && parts[1] in 0..99 && parts[2] in 0..99) {
    "The tessera version must be X.Y.Z with Y and Z below 100, for the versionCode; it's $appVersion"
  }
  parts[0] * 10000 + parts[1] * 100 + parts[2]
}

val releaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").associateWith { name ->
  val env = "TESSERA_SIGNING_" + name.replace(Regex("([A-Z])"), "_$1").uppercase()
  providers.gradleProperty("tessera.signing.$name").orNull?.takeIf { it.isNotBlank() }
    ?: providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
}

dependencies {
  implementation(projects.sharedUI)
  debugImplementation(projects.demo)

  implementation(libs.androidx.activity.compose)
  implementation(libs.koin.android)

  implementation(libs.compose.uiToolingPreview)
  debugImplementation(libs.compose.uiTooling)

  testImplementation(libs.kotlin.test.junit)
}

android {
  namespace = "dev.marzahn.sankaku"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "dev.marzahn.tessera"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = appVersionCode
    versionName = appVersion
  }
  if (releaseSigning.values.all { it != null }) {
    signingConfigs {
      create("release") {
        storeFile = file(releaseSigning.getValue("storeFile")!!)
        storePassword = releaseSigning["storePassword"]
        keyAlias = releaseSigning["keyAlias"]
        keyPassword = releaseSigning["keyPassword"]
      }
    }
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.findByName("release")
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
  mustRunAfter(":checkAndroidReleaseProperties", ":checkMacReleaseProperties")
}
