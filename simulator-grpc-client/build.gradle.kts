import com.google.protobuf.gradle.id

plugins {
  kotlin("jvm")
  id("com.google.protobuf")
  id("com.ncorti.ktfmt.gradle")
  id("maven-publish")
  application
}

ktfmt { googleStyle() }

val grpcVersion = "1.68.1"
val grpcKotlinVersion = "1.4.1"
val protobufVersion = "3.25.5"

// Share the .proto definitions with the server module rather than duplicating.
sourceSets { main { proto { srcDir("../simulator-grpc-server/src/main/proto") } } }

dependencies {
  api("io.grpc:grpc-stub:$grpcVersion")
  api("io.grpc:grpc-protobuf:$grpcVersion")
  api("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
  api("com.google.protobuf:protobuf-kotlin:$protobufVersion")
  implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("ch.qos.logback:logback-classic:1.6.1")

  testImplementation(kotlin("test"))
  testImplementation("org.assertj:assertj-core:3.27.7")
}

kotlin { jvmToolchain(21) }

tasks.test { useJUnitPlatform() }

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }
  plugins {
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" }
    id("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar" }
  }
  generateProtoTasks {
    all().forEach {
      it.plugins {
        id("grpc")
        id("grpckt")
      }
      it.builtins { id("kotlin") }
    }
  }
}

application {
  mainClass.set("com.github.prule.acc.client.simulator.grpc.client.SimulatorGrpcClientCliKt")
  applicationName = "simulator-grpc-client"
}

tasks.register<JavaExec>("runSimulatorGrpcClient") {
  group = "application"
  description = "Run the simulator gRPC client CLI."
  mainClass.set("com.github.prule.acc.client.simulator.grpc.client.SimulatorGrpcClientCliKt")
  classpath = sourceSets["main"].runtimeClasspath
  standardInput = System.`in`
}

publishing {
  publications { create<MavenPublication>("maven") { from(components["java"]) } }
  repositories { mavenLocal() }
}
