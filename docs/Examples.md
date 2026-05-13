# Examples

Runnable example apps that exercise the library end-to-end. Useful as starter wiring and as smoke tests when verifying a fresh checkout works against your environment.

Located under [`src/main/kotlin/com/github/prule/acc/client/example/`](../acc-client-core/src/main/kotlin/com/github/prule/acc/client/example/).

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

## See also

- [ListenerRecipes.md](ListenerRecipes.md) — more listener patterns (lap tracker, telemetry exporter, overtake detector).
- [Listeners.md](Listeners.md) — listener interface contracts.
- [ClientContext.md](ClientContext.md) — full field reference.
