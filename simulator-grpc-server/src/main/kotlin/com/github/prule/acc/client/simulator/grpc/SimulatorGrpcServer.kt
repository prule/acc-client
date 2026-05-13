package com.github.prule.acc.client.simulator.grpc

import com.github.prule.acc.client.simulator.AccSimulatorConfiguration
import com.github.prule.acc.client.simulator.FileSource
import io.grpc.Server
import io.grpc.ServerBuilder
import org.slf4j.LoggerFactory

private const val DEFAULT_GRPC_PORT = 50051
private const val DEFAULT_SIM_PORT = 9000
private const val DEFAULT_PASSWORD = "asd"
private const val DEFAULT_DELAY_MS = 10L
private const val DEFAULT_PLACEHOLDER_FILE = ""

/**
 * Boots the simulator gRPC server. CLI flags:
 *
 *     --grpc-port=<int>             (default 50051)
 *     --sim-port=<int>              (default 9000)
 *     --password=<string>           (default "asd")
 *     --delay-ms=<long>             (default 10)
 *     --max-events=<int>            (default Int.MAX_VALUE)
 *     --only-player-events          (flag; defaults to false)
 *
 * The server starts idle - no simulator is running until a `Start` RPC arrives.
 */
fun main(args: Array<String>) {
  val cli = CliArgs.parse(args)
  val defaults =
    AccSimulatorConfiguration(
      port = cli.simPort,
      connectionPassword = cli.password,
      // Placeholder; every Start RPC supplies its own file path.
      playbackEventsFile = FileSource(DEFAULT_PLACEHOLDER_FILE),
      delay = cli.delayMs,
      maxEvents = cli.maxEvents,
      onlyPlayerEvents = cli.onlyPlayerEvents,
    )
  SimulatorGrpcServer(cli.grpcPort, defaults).run()
}

class SimulatorGrpcServer(
  private val grpcPort: Int,
  private val defaults: AccSimulatorConfiguration,
) {
  private val logger = LoggerFactory.getLogger(javaClass)
  private val service = SimulatorService(defaults)
  private val server: Server = ServerBuilder.forPort(grpcPort).addService(service).build()

  fun run() {
    server.start()
    logger.info("Simulator gRPC server listening on :{}", grpcPort)
    Runtime.getRuntime()
      .addShutdownHook(
        Thread {
          logger.info("Shutdown signal received; stopping simulator and gRPC server")
          service.shutdown()
          server.shutdown()
        }
      )
    server.awaitTermination()
  }
}

private data class CliArgs(
  val grpcPort: Int = DEFAULT_GRPC_PORT,
  val simPort: Int = DEFAULT_SIM_PORT,
  val password: String = DEFAULT_PASSWORD,
  val delayMs: Long = DEFAULT_DELAY_MS,
  val maxEvents: Int = Int.MAX_VALUE,
  val onlyPlayerEvents: Boolean = false,
) {
  companion object {
    fun parse(args: Array<String>): CliArgs {
      var out = CliArgs()
      for (arg in args) {
        out =
          when {
            arg.startsWith("--grpc-port=") -> out.copy(grpcPort = arg.substringAfter('=').toInt())
            arg.startsWith("--sim-port=") -> out.copy(simPort = arg.substringAfter('=').toInt())
            arg.startsWith("--password=") -> out.copy(password = arg.substringAfter('='))
            arg.startsWith("--delay-ms=") -> out.copy(delayMs = arg.substringAfter('=').toLong())
            arg.startsWith("--max-events=") -> out.copy(maxEvents = arg.substringAfter('=').toInt())
            arg == "--only-player-events" -> out.copy(onlyPlayerEvents = true)
            arg == "--help" || arg == "-h" -> {
              printUsage()
              kotlin.system.exitProcess(0)
            }
            else -> error("Unknown argument: $arg (try --help)")
          }
      }
      return out
    }

    private fun printUsage() {
      println(
        """
        Usage: simulator-grpc-server [options]
          --grpc-port=<int>      gRPC listen port (default $DEFAULT_GRPC_PORT)
          --sim-port=<int>       simulator UDP port (default $DEFAULT_SIM_PORT)
          --password=<string>    connection password (default "$DEFAULT_PASSWORD")
          --delay-ms=<long>      ms between emitted messages (default $DEFAULT_DELAY_MS)
          --max-events=<int>     cap on events per session
          --only-player-events   only emit focused-player events
        """
          .trimIndent()
      )
    }
  }
}
