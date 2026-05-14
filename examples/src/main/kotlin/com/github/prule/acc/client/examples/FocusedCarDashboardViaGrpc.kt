package com.github.prule.acc.client.examples

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import com.github.prule.acc.client.AccClient
import com.github.prule.acc.client.AccClientConfiguration
import com.github.prule.acc.client.ClientContext
import com.github.prule.acc.client.ContextUpdater
import com.github.prule.acc.client.example.FocusedCarDashboard
import com.github.prule.acc.client.simulator.grpc.client.SimulatorGrpcClient
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Same dashboard as [FocusedCarDashboard], but with simulator lifecycle driven over gRPC:
 * 1. Connect to a running `simulator-grpc-server`.
 * 2. Send `Start` with the supplied playback CSV path.
 * 3. Connect [AccClient] over UDP and render the dashboard.
 * 4. On Ctrl-C, send `Stop` to gracefully end the session.
 *
 * Prerequisites:
 * - The gRPC server is running (`./gradlew :simulator-grpc-server:runSimulatorGrpcServer`).
 * - The playback CSV path is **server-side** (the server reads it from its own filesystem).
 *
 * CLI flags (all optional except `--playback-file`):
 * ```
 *   --playback-file=<path>     Server-side CSV path to play back (REQUIRED)
 *   --grpc-host=<host>         default localhost
 *   --grpc-port=<int>          default 50051
 *   --sim-host=<host>          default 127.0.0.1 (where the simulator binds; used by AccClient)
 *   --sim-port=<int>           default 9000
 *   --password=<string>        default "asd"
 *   --delay-ms=<long>          override simulator playback delay
 * ```
 */
fun main(args: Array<String>) {
  val cli = Cli.parse(args)
  setRootLogLevel(Level.WARN)

  val grpc = SimulatorGrpcClient.connect(cli.grpcHost, cli.grpcPort)

  // Make sure the simulator gets stopped even if the JVM is killed via Ctrl-C.
  Runtime.getRuntime()
    .addShutdownHook(
      Thread {
        runCatching { runBlocking { grpc.stop() } }
        runCatching { grpc.close() }
        println() // move past the dashboard's in-place line
        println("Simulator stopped")
      }
    )

  runBlocking {
    grpc.start(
      playbackEventsFile = cli.playbackFile,
      port = cli.simPort,
      connectionPassword = cli.password,
      delayMs = cli.delayMs,
    )
  }

  val context = ClientContext()
  runBlocking {
    AccClient(
        AccClientConfiguration(name = "Dashboard", port = cli.simPort, serverIp = cli.simHost)
      )
      .connect(listOf(ContextUpdater(context), FocusedCarDashboard(context)))
  }
}

private data class Cli(
  val playbackFile: String,
  val grpcHost: String = "localhost",
  val grpcPort: Int = 50051,
  val simHost: String = "127.0.0.1",
  val simPort: Int = 9000,
  val password: String = "asd",
  val delayMs: Long? = null,
) {
  companion object {
    fun parse(args: Array<String>): Cli {
      if (args.any { it == "--help" || it == "-h" }) {
        printUsage()
        kotlin.system.exitProcess(0)
      }
      var playback: String? = null
      var grpcHost = "localhost"
      var grpcPort = 50051
      var simHost = "127.0.0.1"
      var simPort = 9000
      var password = "asd"
      var delayMs: Long? = null
      for (arg in args) {
        when {
          arg.startsWith("--playback-file=") -> playback = arg.substringAfter('=')
          arg.startsWith("--grpc-host=") -> grpcHost = arg.substringAfter('=')
          arg.startsWith("--grpc-port=") -> grpcPort = arg.substringAfter('=').toInt()
          arg.startsWith("--sim-host=") -> simHost = arg.substringAfter('=')
          arg.startsWith("--sim-port=") -> simPort = arg.substringAfter('=').toInt()
          arg.startsWith("--password=") -> password = arg.substringAfter('=')
          arg.startsWith("--delay-ms=") -> delayMs = arg.substringAfter('=').toLong()
          else -> error("Unknown argument: $arg (try --help)")
        }
      }
      val file = playback ?: error("--playback-file=<path> is required")
      return Cli(file, grpcHost, grpcPort, simHost, simPort, password, delayMs)
    }

    private fun printUsage() {
      println(
        """
        Usage: focused-car-dashboard-via-grpc --playback-file=<path> [options]
          --playback-file=<path>   Server-side CSV path (REQUIRED)
          --grpc-host=<host>       default localhost
          --grpc-port=<int>        default 50051
          --sim-host=<host>        default 127.0.0.1
          --sim-port=<int>         default 9000
          --password=<string>      default "asd"
          --delay-ms=<long>        override playback delay
        """
          .trimIndent()
      )
    }
  }
}

private fun setRootLogLevel(level: Level) {
  val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as LogbackLogger
  root.level = level
}
