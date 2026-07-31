import com.google.protobuf.gradle.id

plugins {
  kotlin("jvm")
  id("com.google.protobuf")
  id("com.ncorti.ktfmt.gradle")
  application
  `jvm-test-suite`
}

testing {
  suites {
    val test by getting(JvmTestSuite::class) { useJUnitJupiter("5.11.4") }
  }
}

ktfmt { googleStyle() }

val grpcVersion = "1.68.1"
val grpcKotlinVersion = "1.4.1"
val protobufVersion = "4.35.1"

dependencies {
  implementation(project(":acc-client-core"))

  implementation("io.grpc:grpc-stub:$grpcVersion")
  implementation("io.grpc:grpc-protobuf:$grpcVersion")
  implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
  implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
  implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  implementation("ch.qos.logback:logback-classic:1.6.1")

  testImplementation("org.assertj:assertj-core:3.27.7")
  testImplementation("io.grpc:grpc-inprocess:$grpcVersion")
}

kotlin { jvmToolchain(21) }

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
  mainClass.set("com.github.prule.acc.client.simulator.grpc.SimulatorGrpcServerKt")
  applicationName = "simulator-grpc-server"
}

tasks.register<JavaExec>("runSimulatorGrpcServer") {
  group = "application"
  description = "Run the simulator gRPC server (defaults: gRPC :50051, simulator :9000)."
  mainClass.set("com.github.prule.acc.client.simulator.grpc.SimulatorGrpcServerKt")
  classpath = sourceSets["main"].runtimeClasspath
  systemProperty("logback.configurationFile", "../acc-client-core/logback-info.xml")
  // Pass CLI args via -PgrpcArgs="--port=50051 ..." or use `gradle ... --args="..."`
}
