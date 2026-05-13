# Testing Patterns

Reference for writing tests against this codebase. Two complementary fixture strategies.

## Stack

| Tool | Used for |
|---|---|
| JUnit 5 (`useJUnitPlatform()`) | Test runner. |
| AssertJ | Fluent assertions. |
| Mockito-Kotlin (`mockito-kotlin:5.4.0`) | Behavior verification on simple interfaces. Used by older tests. |
| MockK (`mockk:1.14.9`) | Behavior verification, especially when relaxed mocking is needed for Kaitai-generated nested types. |
| `kotlinx-coroutines-test` | Coroutine `runTest` helper. |

Both Mockito-Kotlin and MockK are available — pick per test. New tests in this codebase lean on MockK because relaxed mocks handle Kaitai's nested wrapper types more easily.

## Two fixture strategies

### Strategy A — mocks

Stub the methods you care about, leave the rest unset. Fast, focused, brittle to API changes.

Use when: testing your listener's reaction to a specific message field or shape, and the message body has only a few fields you actually read.

```kotlin
val context = ClientContext()
val updater = ContextUpdater(context)
val sender = mockk<MessageSender>(relaxed = true)

val body = mockk<AccBroadcastingInbound.RegistrationResult>()
every { body.connectionId() } returns 7

val msg = mockk<AccBroadcastingInbound>()
every { msg.msgType() } returns AccBroadcastingInbound.InboundMsgType.REGISTRATION_RESULT
every { msg.body() } returns body

updater.onMessage(byteArrayOf(1, 2, 3), msg, sender)

assertThat(context.connectionId).isEqualTo(7)
verify(exactly = 2) { sender.send(any()) }   // entry list + track data refresh
```

Pros:
- Fast.
- Doesn't depend on the acc-messages binary protocol being stable.
- Easy to construct edge cases (negative ids, empty strings).

Cons:
- Mocks drift from reality. If the dep changes a method name, your test still passes but production breaks.
- Nested types (`StringData`, `Driver`, `CameraSet`) are hard to mock — you'd need to know the exact class names.

Use **relaxed mocks** (`mockk<X>(relaxed = true)`) when the listener walks a deep object graph and you don't care about most fields:

```kotlin
val car = mockk<AccBroadcastingInbound.EntryListCar>(relaxed = true)
every { car.carId() } returns 99
// teamName(), drivers(), cupCategory() etc. all return mock-default values.
```

### Strategy B — real recorded bytes

Parse hex-encoded UDP frames from the bundled playback CSV (or your own recordings). Slow-ish, robust, exercises the real parser.

Use when: testing logic that walks parsed message bodies (decoders, session detector, recording) where you'd otherwise have to mock too much structure.

```kotlin
private fun parse(hex: String): Pair<ByteArray, AccBroadcastingInbound> {
  val bytes = hex.hexToByteArray()
  val msg = AccBroadcastingInbound(
    ByteBufferKaitaiStream(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
  )
  return bytes to msg
}

@Test
fun `fires onSessionStart with real preamble`() {
  val context = ClientContext()
  val updater = ContextUpdater(context)
  val listener = mockk<SessionEventListener>(relaxed = true)
  val detector = SessionDetector(context, listOf(listener))
  val sender = mockk<MessageSender>(relaxed = true)

  // Real bytes from acc-client-core/src/main/resources/.../playback-events.csv
  val (tdBytes, tdMsg) = parse("05120000000d005265642042756c6c2052696e67...")
  updater.onMessage(tdBytes, tdMsg, sender)

  val (carBytes, carMsg) = parse("060000010c00426c61636b2046616c636f6e...")
  updater.onMessage(carBytes, carMsg, sender)

  val (rtBytes, rtMsg) = parse("02000000000a0504988b49f00bae480d000000...")
  detector.onMessage(rtBytes, rtMsg, sender)

  verify(exactly = 1) {
    listener.onSessionStart(match {
      it.track.name == "Red Bull Ring" && it.cars.containsKey(0)
    })
  }
}
```

Pros:
- Exercises the real Kaitai parser end-to-end.
- Works for any field on the body without per-field stubbing.
- Failure means a real protocol assumption broke — useful signal.

Cons:
- Hex strings are opaque. Comment what each row represents.
- Tied to one specific recording — changing it may invalidate tests.

## Where to find real bytes

Bundled fixture: `acc-client-core/src/main/resources/com/github/prule/acc/client/simulator/playback-events.csv`.

One row per inbound type (1–7). Open in any editor — the `hex` column is the third field. Strip newlines/quotes before pasting into a test.

For more variety, capture your own using `RecordingSessionListener` against a live ACC session.

## Hex helpers

Kotlin 2.0+ stdlib has `String.hexToByteArray()` and `ByteArray.toHexString()` as stable API. No `@OptIn` needed in this project (Kotlin 2.3.20).

```kotlin
"deadbeef".hexToByteArray()         // ByteArray of 4 bytes
byteArrayOf(0xCA.toByte()).toHexString()   // "ca"
```

## Testing patterns by component

### MessageListener implementations

Two-arg `onMessage(bytes, message, sender)`. Call directly in a test — no need to spin up a socket or coroutine.

```kotlin
@Test fun `forwards on match`() {
  val inner = mockk<MessageListener<MyBody>>(relaxed = true)
  val listener = MyFilter(listeners = listOf(inner))
  listener.onMessage(byteArrayOf(), inboundOf(MyBody()), mockk())
  verify { inner.onMessage(any(), any(), any()) }
}
```

### SessionEventListener implementations

Drive them through a real `SessionDetector` with a real or stubbed `ClientContext`. Or call their methods directly if you only want to test a single hook.

```kotlin
@Test fun `LapTracker resets on session start`() {
  val tracker = LapTracker(ClientContext())
  tracker.laps += /* fake lap */
  tracker.onSessionStart(samplePreamble())
  assertThat(tracker.laps).isEmpty()
}
```

### ContextUpdater

Mostly state-transition tests — feed messages, assert `ClientContext` shape after.

See `acc-client-core/src/test/kotlin/.../ContextUpdaterTest.kt` for the canonical examples (registration, track change, entry list eviction, raw-bytes deep copy).

### SessionDetector

Strategy B works best here — phase-transition logic depends on real `RealtimeUpdate.phase()` enum values. See `acc-client-core/src/test/kotlin/.../SessionDetectorTest.kt`.

### AccClient

The connect loop is intrinsically hard to unit-test — it's a `while(true)` over a UDP socket. The existing `AccClientTest` only checks that `stop()` flips a flag.

Integration option: spin up `AccSimulator` in a background coroutine, point an `AccClient` at it, drive the test via the listeners you wire in.

```kotlin
@Test
fun `client receives playback events from simulator`() = runTest {
  val sim = AccSimulator(AccSimulatorConfiguration(
    port = 0,                  // any free port — read back from sim.socket if needed
    connectionPassword = "asd",
    playbackEventsFile = ClasspathSource(
      "com/github/prule/acc/client/simulator/playback-events.csv"
    ),
  ))
  // ... start sim, start client, await all messages, assert.
  // Currently AccSimulator binds to a fixed port from config — set a high port to avoid clashes.
}
```

## Tips

- **Don't share a `ClientContext` across tests.** Construct a fresh one per `@Test` to avoid order-dependent state leakage.
- **Don't mock `AccBroadcastingClient`** in tests of `ContextUpdater` — its outbound builders return opaque byte arrays. The test only needs to verify `messageSender.send` was called the right number of times.
- **`MessageSender` is trivially mockable** with `relaxed = true` — it's a thin wrapper around `DatagramSocket.send`.
- **For mocks of `AccBroadcastingInbound.X` body classes**, MockK works without ceremony. Mockito works too but requires no opt-ins for non-final classes (Kaitai-generated Java is non-final by default).
- **Threading** — the production receive loop runs on `Dispatchers.IO`, but listener tests typically call `onMessage` synchronously on the test thread. Don't schedule on `Dispatchers.IO` in tests unless you're testing concurrency itself.

## Running tests

```bash
./gradlew test
```

Single test:

```bash
./gradlew test --tests com.github.prule.acc.client.ContextUpdaterTest
```

With output:

```bash
./gradlew test --info
```

Reports land at `build/reports/tests/test/index.html`.
