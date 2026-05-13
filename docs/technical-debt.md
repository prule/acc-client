# Technical debt

Open items worth cleaning up. Mark items as **DONE** when resolved rather than removing them, so history is preserved.

## Open

- **`AccSimulator` placeholder file path on server startup.** [`SimulatorGrpcServer`](../simulator-grpc-server/src/main/kotlin/com/github/prule/acc/client/simulator/grpc/SimulatorGrpcServer.kt) builds a default `AccSimulatorConfiguration` with `FileSource("")` as a placeholder, since every `Start` RPC supplies its own file. The empty path would blow up if anything tried to read it before a `Start` arrived. Consider modeling the default config without a playback source (or making `playbackEventsFile` nullable / supplied at `start()` time instead).
## Done

- **`EventPlayer` uses `GlobalScope`.** DONE. `AccSimulator` now owns a `CoroutineScope` and passes it down; `stop()` cancels it before closing the socket, so the playback coroutine exits cleanly instead of error-spamming on the closed socket.
