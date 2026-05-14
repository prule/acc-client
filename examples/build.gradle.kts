// --- Examples module ----------------------------------------------------------------------------
//
// Runnable example apps that combine `acc-client-core` (the UDP library + simulator) with
// `simulator-grpc-client` (lifecycle control over gRPC). The module is unpublished so neither
// library picks the other up as a transitive dep - external consumers depending on
// `com.github.prule:acc-client` or `com.github.prule:simulator-grpc-client` stay lean.
//
// Each example below has a Gradle task. Quick-start workflows:
//
// 1. FocusedCarDashboard (direct UDP, against a running ACC server or the bundled simulator):
//      ./gradlew :acc-client-core:runAccSimulator            # terminal 1 (offline playback)
//      ./gradlew :examples:runFocusedCarDashboard            # terminal 2
//
// 2. FocusedCarDashboardViaGrpc (drives the simulator via gRPC):
//      ./gradlew :examples:runSimulatorGrpcServer            # terminal 1 (idle until Start)
//      ./gradlew :examples:runFocusedCarDashboardViaGrpc     # terminal 2
//    The task ships with a default --playback-file pointing at a CSV under ./recordings/. Override
//    with --args="--playback-file=<other.csv>". The bundled fixture lives at
//    acc-client-core/src/main/resources/com/github/prule/acc/client/simulator/playback-events.csv
//
// See docs/Examples.md for the full walkthrough.
// ------------------------------------------------------------------------------------------------

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

// Isolated classpath for the `runSimulatorGrpcServer` alias below. Kept separate from `main` so the
// server module's generated proto stubs don't collide with the client's on the example sources'
// classpath.
val grpcServerRuntime: Configuration by configurations.creating

dependencies { grpcServerRuntime(project(":simulator-grpc-server")) }

kotlin { jvmToolchain(21) }

application {
  // Default `run` target points at the gRPC-driven dashboard - the more end-to-end of the two.
  mainClass.set("com.github.prule.acc.client.examples.FocusedCarDashboardViaGrpcKt")
  applicationName = "focused-car-dashboard-via-grpc"
}

tasks.register<JavaExec>("runFocusedCarDashboard") {
  group = "application"
  description =
    "Live single-line CLI dashboard. Requires a UDP source on :9000 (real ACC, or " +
      ":acc-client-core:runAccSimulator in another terminal)."
  mainClass.set("com.github.prule.acc.client.examples.FocusedCarDashboardKt")
  classpath = sourceSets["main"].runtimeClasspath
  standardInput = System.`in`
}

tasks.register<JavaExec>("runFocusedCarDashboardViaGrpc") {
  group = "application"
  description =
    "Drive the simulator via gRPC and render the focused-car dashboard. " +
      "Requires :examples:runSimulatorGrpcServer in another terminal."
  mainClass.set("com.github.prule.acc.client.examples.FocusedCarDashboardViaGrpcKt")
  classpath = sourceSets["main"].runtimeClasspath
  standardInput = System.`in`
  // Default playback file — override on the command line with --args="--playback-file=<path>".
  args =
    listOf(
      "--playback-file=recordings/simulator-recording-2026-05-06T11-48-49.206633-race-donington-ferrari.csv"
    )
}

// Mirror of :simulator-grpc-server:runSimulatorGrpcServer so users can find every part of the
// example workflow under :examples:. Runs on an isolated classpath (see grpcServerRuntime above).
tasks.register<JavaExec>("runSimulatorGrpcServer") {
  group = "application"
  description =
    "Boot the simulator gRPC server. Pass flags via --args=\"...\", " +
      "e.g. --args=\"--grpc-port=50051 --sim-port=9000\"."
  mainClass.set("com.github.prule.acc.client.simulator.grpc.SimulatorGrpcServerKt")
  classpath = grpcServerRuntime
}
