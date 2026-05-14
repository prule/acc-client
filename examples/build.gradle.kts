plugins {
  kotlin("jvm")
  id("com.ncorti.ktfmt.gradle")
  application
}

ktfmt { googleStyle() }

dependencies {
  implementation(project(":acc-client-core"))
  implementation(project(":simulator-grpc-client"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("ch.qos.logback:logback-classic:1.5.32")
}

kotlin { jvmToolchain(21) }

application {
  mainClass.set("com.github.prule.acc.client.examples.FocusedCarDashboardViaGrpcKt")
  applicationName = "focused-car-dashboard-via-grpc"
}

tasks.register<JavaExec>("runFocusedCarDashboardViaGrpc") {
  group = "application"
  description =
    "Drive the simulator via gRPC and render the focused-car dashboard. " +
      "Requires a running simulator-grpc-server."
  mainClass.set("com.github.prule.acc.client.examples.FocusedCarDashboardViaGrpcKt")
  classpath = sourceSets["main"].runtimeClasspath
  standardInput = System.`in`
}
