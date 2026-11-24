plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.kotlinx.serialization)
  application
}

dependencies {
  implementation(projects.sharedLogic)
  implementation(libs.ktor.client.okhttp)
  runtimeOnly(libs.slf4j.nop)
}

application {
  mainClass = "dev.marzahn.sankaku.cli.MainKt"
}

tasks.named<JavaExec>("run") {
  workingDir = rootProject.projectDir
}
