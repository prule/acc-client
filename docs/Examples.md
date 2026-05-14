# Examples

Runnable example apps that exercise the library end-to-end. Useful as starter wiring and as smoke tests when verifying a fresh checkout works against your environment.

Located under [`acc-client-core/src/main/kotlin/com/github/prule/acc/client/example/`](../acc-client-core/src/main/kotlin/com/github/prule/acc/client/example/) (library-only examples) and [`examples/src/main/kotlin/com/github/prule/acc/client/examples/`](../examples/src/main/kotlin/com/github/prule/acc/client/examples/) (examples that depend on multiple modules, e.g. gRPC).

## FocusedCarDashboard

Live single-line CLI view of whatever ACC's broadcasting view is currently focused on.

Output (refreshed in place via `\r`):

```
[Red Bull Ring       ] focused=#13  Mercedes-AMG GT3 Evo            G:2   120kmh  L12  lap  11.9%
```

Columns:

| Field | Source |
|---|---|
| Track | `ClientContext.track?.name` |
| Focused car index | `ClientContext.focusedCarIndex` |
| Car model | `CarModelRepository.findById(cars[focusedCarIndex].carModelType).name` |
| Gear | `RealtimeCarUpdate.gear()` — translated: 0=R, 1=N, 2+=1st onwards |
| Speed | `RealtimeCarUpdate.kmh()` |
| Lap number | `RealtimeCarUpdate.laps()` — completed laps for the focused car |
| Lap % | `RealtimeCarUpdate.splinePosition() * 100` |

### Run

Standalone (against the bundled simulator):

```bash
# terminal 1
./gradlew runAccSimulator

# terminal 2
./gradlew runFocusedCarDashboard
```

Against a real ACC server: edit `serverIp` in `FocusedCarDashboard.kt`'s `main()` and run the dashboard task on its own.

### What it proves

If the dashboard line populates with a sensible track + car name + non-zero speed and lap %, the wiring is healthy:

- UDP socket reaches the server.
- Register handshake succeeded (`connectionId` set inside `ClientContext`).
- `ContextUpdater` requested + received entry list and track data.
- `RealtimeCarUpdate` packets are arriving and being parsed.
- `CarModelRepository` recognises the car id (or shows `?` if the id isn't in the bundled CSV — see [CarModels.md](CarModels.md)).

If the line shows `[—]` for track, `?` for the car model, or stays at `0kmh / 0.0%`, something upstream is broken. Drop in a `LoggingListener` ahead of `ContextUpdater` to see raw messages and diagnose.

### Code walk-through

The class is a single `MessageListener<AccBroadcastingInbound>`. Two responsibilities:

1. On `REALTIME_CAR_UPDATE` matching the focused car, capture per-tick fields (gear, speed, spline) and re-render.
2. On any preamble or focus-change message, also re-render so the line refreshes promptly when the track/car changes.

State that survives across messages:

```kotlin
@Volatile private var gear: Int = 1
@Volatile private var kmh: Int = 0
@Volatile private var spline: Float = 0f
@Volatile private var laps: Int = 0
```

Track + car model + focused-car-index come from `ClientContext` directly — no caching needed in the listener itself.

Wiring (the `main()` at the bottom of the file):

```kotlin
suspend fun main() {
  val context = ClientContext()
  AccClient(AccClientConfiguration(name = "Dashboard", port = 9000, serverIp = "127.0.0.1"))
    .connect(
      listOf(
        ContextUpdater(context),
        FocusedCarDashboard(context),
      )
    )
}
```

No `LoggingListener` (would clutter the dashboard line), no `SessionDetector` (the dashboard is happy to render outside an active session — useful for showing the focused car in the lobby / pit).

### Things to try

- Add `SessionDetector(context, listOf(RecordingSessionListener(Path.of("./recordings"))))` to the listener list to record while watching.
- Replace `@Volatile var gear` etc. with a `RealtimeCarUpdate?` reference if you want richer telemetry (delta, position, splits, world coords). See [WireProtocol.md](WireProtocol.md) for the full body shape.
- Wrap the `FocusedCarDashboard` in a `FilteredMessageListener<RealtimeCarUpdate>` instead of doing type discrimination inside `onMessage`. See the recipe in [ListenerRecipes.md](ListenerRecipes.md).

## FocusedCarDashboardViaGrpc

Same dashboard, but the simulator's lifecycle is driven over gRPC instead of requiring a separate `runAccSimulator` task. Lives in the [`examples`](../examples/) module so it can depend on both `acc-client-core` and `simulator-grpc-client` without those modules picking each other up as transitive deps.

### Architecture

```
  ┌────────────────────────────────┐                 ┌──────────────────────────┐
  │ examples:                      │ ── gRPC ─────►  │ simulator-grpc-server    │
  │  FocusedCarDashboardViaGrpc    │  Start/Stop/    │  (wraps AccSimulator)    │
  │                                │   Status        │                          │
  │  ┌──────────────────────────┐  │                 │  ┌────────────────────┐  │
  │  │ SimulatorGrpcClient      │  │                 │  │ AccSimulator       │  │
  │  └──────────────────────────┘  │                 │  │  (UDP on :9000)    │  │
  │                                │                 │  └─────────┬──────────┘  │
  │  ┌──────────────────────────┐  │ ◄── UDP ────────┼────────────┘             │
  │  │ AccClient + dashboard    │  │                 │                          │
  │  └──────────────────────────┘  │                 │                          │
  └────────────────────────────────┘                 └──────────────────────────┘
```

### Run (using the bundled fixture)

The repo ships a small recorded session at `acc-client-core/src/main/resources/com/github/prule/acc/client/simulator/playback-events.csv` that the gRPC server can read directly. From the repo root:

```bash
# terminal 1 - start the gRPC server (boots idle; no simulator running yet)
./gradlew :simulator-grpc-server:runSimulatorGrpcServer

# terminal 2 - tell the server to start the simulator + run the dashboard
./gradlew :examples:runFocusedCarDashboardViaGrpc \
  --args="--playback-file=acc-client-core/src/main/resources/com/github/prule/acc/client/simulator/playback-events.csv"
```

Expected output in terminal 2 (the dashboard line refreshes in place via `\r`):

```
Focused-car dashboard — Ctrl-C to stop
[Red Bull Ring       ] focused=#13  Mercedes-AMG GT3 Evo            G:3   120kmh  L12  lap 11.9%
```

Press **Ctrl-C** to stop. The JVM shutdown hook sends a `Stop` RPC, which makes the simulator emit a final `REALTIME_UPDATE` with phase=`SESSION_OVER` so any `SessionDetector` on the client side fires `onSessionStop` cleanly before the socket closes.

### Run (using your own recording)

If you've recorded a session via [Recording.md](Recording.md), point `--playback-file` at the CSV. The path is **server-side** (read from the gRPC server's working directory), so put a full path or run the server from the directory that holds the file:

```bash
./gradlew :examples:runFocusedCarDashboardViaGrpc \
  --args="--playback-file=/Users/me/recordings/silverstone-race.csv"
```

### CLI flags

| Flag | Default | Purpose |
|---|---|---|
| `--playback-file=<path>` | (required) | Server-side CSV path |
| `--grpc-host=<host>` | `localhost` | Where to reach the gRPC server |
| `--grpc-port=<int>` | `50051` | gRPC server port |
| `--sim-host=<host>` | `127.0.0.1` | Where `AccClient` connects (must match what the simulator binds to) |
| `--sim-port=<int>` | `9000` | UDP port the simulator binds to |
| `--password=<string>` | `asd` | ACC broadcasting connection password |
| `--delay-ms=<long>` | (server default) | Override the per-message playback delay |

`--sim-port` and `--password` are forwarded to the gRPC `Start` request, so they override the server's startup defaults too — both the simulator and `AccClient` end up using the same values.

### Troubleshooting

| Symptom | Likely cause |
|---|---|
| `UNAVAILABLE: io exception` from the gRPC client | The gRPC server isn't running, or `--grpc-port` doesn't match what the server is listening on. |
| Server log says `file not found: ...` | The path passed in `--playback-file` doesn't exist from the **server's** working directory. Use an absolute path. |
| Dashboard shows `[—] focused=#— ? G:N 0kmh  L0 lap  0.0%` and stays there | The simulator started but never emitted a `REALTIME_UPDATE` matching the focused car. Check the server logs - the CSV may only contain preamble rows, or `--delay-ms` is so high the client's 2s `soTimeout` fires first. |
| "Address already in use" on simulator start | Another process is already on `--sim-port`. Pick a different port. |

See [SimulatorGrpcControl.md](SimulatorGrpcControl.md) for the full gRPC surface (Start / Stop / Status with all knobs).

## See also

- [ListenerRecipes.md](ListenerRecipes.md) — more listener patterns (lap tracker, telemetry exporter, overtake detector).
- [Listeners.md](Listeners.md) — listener interface contracts.
- [ClientContext.md](ClientContext.md) — full field reference.
