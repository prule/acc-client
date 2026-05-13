# Technical debt

Open items worth cleaning up. Mark items as **DONE** when resolved rather than removing them, so history is preserved.

## Open

- **`AccSimulator` placeholder file path on server startup.** [`SimulatorGrpcServer`](../simulator-grpc-server/src/main/kotlin/com/github/prule/acc/client/simulator/grpc/SimulatorGrpcServer.kt) builds a default `AccSimulatorConfiguration` with `FileSource("")` as a placeholder, since every `Start` RPC supplies its own file. The empty path would blow up if anything tried to read it before a `Start` arrived. Consider modeling the default config without a playback source (or making `playbackEventsFile` nullable / supplied at `start()` time instead).
- **`EventPlayer` uses `GlobalScope`.** [`EventPlayer.sendPackets`](../acc-client-core/src/main/kotlin/com/github/prule/acc/client/simulator/EventPlayer.kt) launches on `GlobalScope`. This means the playback coroutine is not tied to the simulator's lifecycle — stopping the simulator closes the socket but doesn't cancel the in-flight playback loop, so it continues iterating until the CSV runs out (sends become no-ops on the closed socket). Switch to a `CoroutineScope` owned by `AccSimulator` so `stop()` can cancel it.

## Done
