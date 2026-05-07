# Logging

Reference for the slf4j + logback setup used by the library.

## Backend

- API: `org.slf4j:slf4j-api` (transitive).
- Backend: `ch.qos.logback:logback-classic` (declared in `build.gradle.kts`).
- Default config: [`src/main/resources/com/github/prule/acc/client/logback.xml`](../src/main/resources/com/github/prule/acc/client/logback.xml).

The shipped config sets the root level to `debug` and writes to STDOUT with the pattern:

```
%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} -%kvp- %msg%n
```

Example line:

```
22:23:38.166 [main] DEBUG i.g.prule.acc.client.LoggingListener -- Received bytes: 010500000001010000 {"msgType":"REGISTRATION_RESULT",...}
```

## Caveat for downstream consumers

The bundled `logback.xml` lives at `src/main/resources/com/github/prule/acc/client/logback.xml` — that is **not** on the standard Logback discovery path (which is `logback.xml` at the root of the classpath). When this library is consumed as a JAR, downstream apps will not pick it up automatically.

Downstream apps should provide their own `logback.xml` at their classpath root.

When running this project's `runAccClient` / `runAccSimulator` Gradle tasks directly, the bundled config is also not on the discovery path — Logback uses its built-in default config (root `DEBUG` to STDOUT with a similar pattern). The bundled file is currently primarily a reference / starting point.

## Tuning levels

Set per-package levels in your downstream `logback.xml`:

```xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Library-wide -->
  <logger name="com.github.prule.acc.client" level="info"/>

  <!-- Quiet down individual noisy loggers -->
  <logger name="com.github.prule.acc.client.LoggingListener" level="warn"/>
  <logger name="com.github.prule.acc.client.MessageReceiver" level="info"/>

  <root level="warn">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

## Logger inventory

Names per file (each uses `LoggerFactory.getLogger(javaClass)`):

| Logger | Common output |
|---|---|
| `com.github.prule.acc.client.AccClient` | `Connecting to server`, `Opening socket and registering`, `Sent register command`, `Session ended, waiting before reconnecting`. |
| `com.github.prule.acc.client.MessageReceiver` | `Socket timed out. Session ended.`, parse errors. |
| `com.github.prule.acc.client.MessageSender` | `Sending bytes: <hex>` per outbound. Verbose. |
| `com.github.prule.acc.client.LoggingListener` | `Received bytes: <hex> <json>` per inbound message. **Very verbose.** Disable in production. |
| `com.github.prule.acc.client.ContextUpdater` | `Registered: connectionId=N`, `Track data cached: <name>`, `Track changed: X -> Y`, `Entry list cached: N cars`, `Car entry cached: carId=N`. |
| `com.github.prule.acc.client.SessionDetector` | `Session started (phase=...)`, `Session ended (phase=...)`, `Preamble ready; firing deferred onSessionStart`, `Connection lost during active session`. |
| `com.github.prule.acc.client.RecordingSessionListener` | `Recording session to <path>`, `Session recording closed`. |
| `com.github.prule.acc.client.CsvWriterListener` | `Writing <filename>`. |
| `com.github.prule.acc.client.simulator.AccSimulator` | `Starting simulator on port N`. |
| `com.github.prule.acc.client.simulator.PlaybackEventsRepository` | `Loading <path>`. |
| `com.github.prule.acc.client.simulator.RegisterListener` | Register handshake reception. |

## Recommended levels for common scenarios

| Scenario | Suggested config |
|---|---|
| Local dev — see everything | Root `DEBUG`. Default. |
| Production — quiet steady-state, loud on transitions | `LoggingListener` + `MessageSender` at `WARN`; everything else at `INFO`; root `WARN`. |
| Debugging session start | `SessionDetector` + `ContextUpdater` at `DEBUG`; everything else at `INFO`. |
| Debugging recording | `RecordingSessionListener` + `CsvWriterListener` at `DEBUG`. |

## Programmatic level changes

If you want to set log levels from code (e.g. an example app or a CLI flag) rather than via XML config, cast the slf4j root logger to logback's concrete `Logger` and set `.level`:

```kotlin
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun setRootLogLevel(level: Level) {
  val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as LogbackLogger
  root.level = level
}

// usage
setRootLogLevel(Level.WARN)
```

Per-logger setting works the same way — pass a non-root name to `LoggerFactory.getLogger(...)` first:

```kotlin
val l = LoggerFactory.getLogger("com.github.prule.acc.client.LoggingListener") as LogbackLogger
l.level = Level.OFF
```

The cast assumes logback is the slf4j backend on the classpath. It is for this library; downstream consumers using a different backend would need that backend's equivalent API. Already used by `FocusedCarDashboard` to keep its single-line CLI output clean.

## Disabling LoggingListener

`LoggingListener` itself is just a `MessageListener` you wire in. The simplest way to silence it is not to register it:

```kotlin
AccClient(config).connect(
  listOf(
    // LoggingListener(),     // ← omit
    ContextUpdater(context),
    SessionDetector(context, listOf(...)),
  )
)
```

If you want it for some message types only, wrap it in a `FilteredMessageListener`.

## Structured logging / MDC

The library doesn't currently use slf4j MDC or key-value pairs. The `%kvp` token in the bundled `logback.xml` pattern is a no-op for now. If you want structured output (e.g. for log aggregation), feel free to:

- Replace the appender with a JSON-encoder one (e.g. `logstash-logback-encoder`).
- Wrap consumer listeners with your own MDC-setting decorator.

## See also

- [Logback manual](https://logback.qos.ch/manual/configuration.html) — full configuration reference.
- [`LoggingListener.kt`](../src/main/kotlin/com/github/prule/acc/client/LoggingListener.kt) — the source.
