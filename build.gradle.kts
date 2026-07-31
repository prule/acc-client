plugins {
  kotlin("jvm") version "2.4.10" apply false
  kotlin("plugin.serialization") version "2.4.10" apply false
  id("org.jetbrains.dokka") version "2.2.0" apply false
  id("com.ncorti.ktfmt.gradle") version "0.26.0" apply false
  id("com.google.protobuf") version "0.9.5" apply false
}

allprojects {
  group = "com.github.prule"
  version = "main-SNAPSHOT"
}
