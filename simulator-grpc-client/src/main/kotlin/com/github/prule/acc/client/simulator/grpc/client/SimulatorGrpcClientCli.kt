package com.github.prule.acc.client.simulator.grpc.client

import com.github.prule.acc.client.simulator.grpc.StatusResponse
import kotlinx.coroutines.runBlocking

/**
 * Tiny CLI for poking the simulator gRPC server. Examples:
 *
 *     simulator-grpc-client status
 *     simulator-grpc-client start ./recordings/race.csv
 *     simulator-grpc-client start ./recordings/race.csv --delay-ms=5 --only-player-events
 *     simulator-grpc-client stop
 *
 * Connection flags (any position):
 *
 *     --host=<host>          (default localhost)
 *     --grpc-port=<int>      (default 50051)
 */
fun main(args: Array<String>) {
  if (args.isEmpty() || args[0] == "--help" || args[0] == "-h") {
    printUsage()
    return
  }
  val command = args[0]
  val rest = args.drop(1)

  val parsed = ParsedArgs.parse(rest)

  SimulatorGrpcClient.connect(parsed.host, parsed.grpcPort).use { client ->
    runBlocking {
      val response =
        when (command) {
          "status" -> client.status()
          "stop" -> client.stop()
          "start" -> {
            val file =
              parsed.positionals.firstOrNull()
                ?: error("'start' requires a playback events file path")
            client.start(
              playbackEventsFile = file,
              port = parsed.port,
              connectionPassword = parsed.password,
              delayMs = parsed.delayMs,
              maxEvents = parsed.maxEvents,
              onlyPlayerEvents = parsed.onlyPlayerEvents,
            )
          }
          else -> {
            printUsage()
            error("Unknown command: $command")
          }
        }
      printStatus(response)
    }
  }
}

private fun printStatus(r: StatusResponse) {
  when (r.state) {
    StatusResponse.State.RUNNING -> {
      val c = r.running
      println("RUNNING")
      println("  file:               ${c.playbackEventsFile}")
      println("  port:               ${c.port}")
      println("  password:           ${c.connectionPassword}")
      println("  delay_ms:           ${c.delayMs}")
      println("  max_events:         ${c.maxEvents}")
      println("  only_player_events: ${c.onlyPlayerEvents}")
    }
    StatusResponse.State.STOPPED,
    StatusResponse.State.UNRECOGNIZED,
    null -> println("STOPPED")
  }
}

private fun printUsage() {
  println(
    """
    Usage: simulator-grpc-client <command> [args] [options]
    Commands:
      start <playback-file>   Start a session (replaces any running one)
      stop                    Stop the running session, if any
      status                  Print current state
    Start options (all optional):
      --port=<int>            Override simulator UDP port
      --password=<string>     Override connection password
      --delay-ms=<long>       Override delay between messages
      --max-events=<int>      Cap events per session
      --only-player-events    Emit only focused-player events
    Connection options:
      --host=<host>           gRPC server host (default localhost)
      --grpc-port=<int>       gRPC server port (default 50051)
    """
      .trimIndent()
  )
}

private data class ParsedArgs(
  val host: String = "localhost",
  val grpcPort: Int = 50051,
  val port: Int? = null,
  val password: String? = null,
  val delayMs: Long? = null,
  val maxEvents: Int? = null,
  val onlyPlayerEvents: Boolean? = null,
  val positionals: List<String> = emptyList(),
) {
  companion object {
    fun parse(args: List<String>): ParsedArgs {
      var out = ParsedArgs()
      val positionals = mutableListOf<String>()
      for (arg in args) {
        out =
          when {
            arg.startsWith("--host=") -> out.copy(host = arg.substringAfter('='))
            arg.startsWith("--grpc-port=") -> out.copy(grpcPort = arg.substringAfter('=').toInt())
            arg.startsWith("--port=") -> out.copy(port = arg.substringAfter('=').toInt())
            arg.startsWith("--password=") -> out.copy(password = arg.substringAfter('='))
            arg.startsWith("--delay-ms=") -> out.copy(delayMs = arg.substringAfter('=').toLong())
            arg.startsWith("--max-events=") -> out.copy(maxEvents = arg.substringAfter('=').toInt())
            arg == "--only-player-events" -> out.copy(onlyPlayerEvents = true)
            arg.startsWith("--") -> error("Unknown option: $arg")
            else -> {
              positionals += arg
              out
            }
          }
      }
      return out.copy(positionals = positionals)
    }
  }
}
