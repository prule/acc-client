plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
    id("maven-publish")
}

group = "com.github.prule"
version = "main-SNAPSHOT"

dependencies {
    api("com.github.prule:acc-messages:main-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:0.15.2")
    implementation("io.github.blackmo18:kotlin-grass-core-jvm:1.0.0")
    implementation("io.github.blackmo18:kotlin-grass-parser-jvm:0.8.0")
    implementation("io.github.blackmo18:kotlin-grass-date-time-jvm:0.8.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}
