plugins {
  kotlin("jvm") version "2.3.20"
  kotlin("plugin.serialization") version "2.3.20"
  id("maven-publish")
  id("org.jetbrains.dokka") version "1.9.20"
}

group = "com.github.prule"

version = "main-SNAPSHOT"

java {
  withSourcesJar()
  withJavadocJar()
}

dependencies {
  api("com.github.prule:acc-messages:main-SNAPSHOT")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
  implementation("ch.qos.logback:logback-classic:1.5.32")
  implementation("com.github.doyaaaaaken:kotlin-csv-jvm:0.15.2")
  implementation("io.github.blackmo18:kotlin-grass-core-jvm:1.0.0")
  implementation("io.github.blackmo18:kotlin-grass-parser-jvm:0.8.0")
  implementation("io.github.blackmo18:kotlin-grass-date-time-jvm:0.8.0")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  testImplementation(kotlin("test"))
  testImplementation("org.assertj:assertj-core:3.27.7")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation("io.mockk:mockk:1.13.11") // Added MockK
}

kotlin { jvmToolchain(21) }

tasks.test { useJUnitPlatform() }

tasks.register<JavaExec>("runAccSimulator") {
  group = "application"
  mainClass.set("com.github.prule.acc.client.simulator.AccSimulatorKt")
  classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runAccClient") {
  group = "application"
  mainClass.set("com.github.prule.acc.client.AccClientKt")
  classpath = sourceSets["main"].runtimeClasspath
}

// Ensure Dokka is used for the Javadoc JAR
tasks.named<Jar>("javadocJar") {
  from(tasks.named("dokkaJavadoc"))
  dependsOn(tasks.named("dokkaJavadoc"))
}

publishing {
  publications { create<MavenPublication>("maven") { from(components["java"]) } }
  repositories { mavenLocal() }
}
