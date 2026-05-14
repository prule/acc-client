# Simulator gRPC Control

Start, stop and query the ACC simulator over gRPC. Useful when the simulator runs as a long-lived
process and another app (test harness, dashboard, automation) needs to drive its lifecycle.

The proto + service implementation live in the `simulator-grpc-server` module, and a Kotlin client
library + CLI live in `simulator-grpc-client`. External JVM apps that just want to control the
simulator only need to depend on `simulator-grpc-client`.

## RPCs

Defined in [simulator-grpc-server/src/main/proto/simulator.proto](../simulator-grpc-server/src/main/proto/simulator.proto):

| RPC      | Behavior                                                                                       |
| -------- | ---------------------------------------------------------------------------------------------- |
| `Start`  | Begin a playback session. If one is already running, it's stopped first. Returns the new state. |
| `Stop`   | Stop the running session. Idempotent - no-op if nothing is running.                            |
| `Status` | Report current state (`RUNNING` / `STOPPED`) and the effective config when running.            |

`StartRequest` has a required `playback_events_file` (server-side filesystem path) plus optional
overrides for every `AccSimulatorConfiguration` knob:

```proto
string  playback_events_file  // required
optional int32  port
optional string connection_password
optional int64  delay_ms
optional int32  max_events
optional bool   only_player_events
```

Unset optional fields fall back to the server's startup defaults.

## Running the server

```sh
./gradlew :simulator-grpc-server:runSimulatorGrpcServer --args="--grpc-port=50051 --sim-port=9000"
```

Available flags: `--grpc-port`, `--sim-port`, `--password`, `--delay-ms`, `--max-events`,
`--only-player-events`. The server boots idle - no simulator runs until a `Start` RPC arrives.

## Using the CLI client

```sh
./gradlew :simulator-grpc-client:runSimulatorGrpcClient --args="start ./recordings/race.csv"
./gradlew :simulator-grpc-client:runSimulatorGrpcClient --args="status"
./gradlew :simulator-grpc-client:runSimulatorGrpcClient --args="stop"
```

Add `--host=<host>` / `--grpc-port=<int>` to target a non-local server.

## Using the client library from another JVM app

Depend on `com.github.prule:simulator-grpc-client:main-SNAPSHOT` and:

```kotlin
SimulatorGrpcClient.connect("localhost", 50051).use { client ->
  runBlocking {
    client.start(
      playbackEventsFile = "/path/to/events.csv",
      delayMs = 5,
      onlyPlayerEvents = true,
    )
    // ...
    client.stop()
  }
}
```

The generated proto messages (`StartRequest`, `StatusResponse`, etc.) are also exported via the
client module's `api` configuration if you'd rather drive the stub directly.

## Lifecycle notes

- `AccSimulator.start()` is non-blocking - the receive loop runs on a background daemon thread.
- `AccSimulator.stop()` does three things in order:
  1. **Emit a `REALTIME_UPDATE` with phase=`SESSION_OVER`** based on the most recent realtime
     update sent during playback. This lets client-side `SessionDetector`s fire `onSessionStop`
     for a clean session shutdown instead of relying purely on socket timeout. (No-op if the
     client never registered and no realtime update was ever sent.)
  2. Cancel the playback coroutine scope so the `EventPlayer` exits without error-spamming on a
     closing socket.
  3. Close the `DatagramSocket`, which unblocks `receive()` and lets the receive loop exit.
- Clients also detect end-of-session via socket timeout if they have one configured
  (`AccClient` uses `socket.soTimeout = 2000`), so the SESSION_OVER frame is an addition, not a
  replacement, for normal connection-loss handling.
- **Only one playback session runs at a time** (mirroring real ACC). If a second client registers
  while a session is still playing, `EventPlayer` emits a `SESSION_OVER` frame to the previous
  client, cancels its playback job, then starts a fresh playback for the new client from the
  beginning of the CSV. The previous client sees a clean session-end; the new client sees a fresh
  session start.
- The gRPC server installs a JVM shutdown hook that stops any running simulator before shutting
  down the gRPC server itself.
