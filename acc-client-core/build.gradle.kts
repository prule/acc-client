plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  id("maven-publish")
  id("org.jetbrains.dokka")
  id("com.ncorti.ktfmt.gradle")
}

// Preserve the published artifact name so existing consumers
// (com.github.prule:acc-client) continue to resolve after the
// multi-module split.
base { archivesName.set("acc-client") }

java {
  withSourcesJar()
  withJavadocJar()
}

ktfmt {
  // Google style - 2 space indentation & automatically adds/removes trailing commas
  googleStyle()
}

dependencies {
  api("com.github.prule:acc-messages:main-SNAPSHOT")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
  implementation("ch.qos.logback:logback-classic:1.6.1")
  implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.10.0")
  implementation("io.github.blackmo18:kotlin-grass-core-jvm:1.0.0")
  implementation("io.github.blackmo18:kotlin-grass-parser-jvm:0.8.0")
  implementation("io.github.blackmo18:kotlin-grass-date-time-jvm:0.8.0")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  testImplementation(kotlin("test"))
  testImplementation("org.assertj:assertj-core:3.27.7")
  testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation("io.mockk:mockk:1.14.11")
}

kotlin { jvmToolchain(21) }

tasks.test { useJUnitPlatform() }

tasks.register<JavaExec>("runAccSimulator") {
  group = "application"
  mainClass.set("com.github.prule.acc.client.simulator.AccSimulatorKt")
  classpath = sourceSets["main"].runtimeClasspath
  systemProperty("logback.configurationFile", "logback-info.xml")
}

tasks.register<JavaExec>("runAccClient") {
  group = "application"
  mainClass.set("com.github.prule.acc.client.AccClientKt")
  classpath = sourceSets["main"].runtimeClasspath
}

// runFocusedCarDashboard now lives in `:examples` alongside the other example apps.

publishing {
  publications { create<MavenPublication>("maven") { from(components["java"]) } }
  repositories { mavenLocal() }
}
