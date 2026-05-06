# Consuming acc-client

How downstream projects depend on this library.

## JitPack

The library is published via [JitPack](https://jitpack.io/#prule/acc-client). Versions:

| Coordinate | Meaning |
|---|---|
| `main-SNAPSHOT` | Latest commit on `main`. Rebuilt on demand. May change without notice. |
| `<tag>` | A git tag. Stable — once published, doesn't change. |

Check available versions at <https://jitpack.io/#prule/acc-client>.

This library transitively depends on [acc-messages](https://github.com/prule/acc-messages), also via JitPack — you do not need to declare it separately, but you do need the JitPack repository configured.

## Gradle (Kotlin DSL)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven("https://jitpack.io")
  }
}

// build.gradle.kts
dependencies {
  implementation("com.github.prule:acc-client:main-SNAPSHOT")
}
```

For a tagged release:

```kotlin
implementation("com.github.prule:acc-client:1.2.3")
```

## Gradle (Groovy)

```groovy
// settings.gradle
dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven { url = 'https://jitpack.io' }
  }
}

// build.gradle
dependencies {
  implementation 'com.github.prule:acc-client:main-SNAPSHOT'
}
```

## Maven

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.prule</groupId>
    <artifactId>acc-client</artifactId>
    <version>main-SNAPSHOT</version>
  </dependency>
</dependencies>
```

## What you get on the classpath

Direct:

- `acc-client` itself (this library).
- `acc-messages` — exposed via `api(...)` in `build.gradle.kts`. You can directly reference `com.github.prule.acc.messages.AccBroadcastingInbound` and friends.

Transitive:

- `kaitai-struct-runtime` — used by `AccBroadcastingInbound`.
- `kotlin-stdlib`, `kotlinx-coroutines-core`, `kotlinx-serialization-json`.
- `jackson-databind` — used by `JsonFormatter`.
- `kotlin-csv-jvm` — used by recorders.
- `kotlin-grass-*` — used by `CarModelRepository`.
- `slf4j-api`, `logback-classic` — see [Logging.md](Logging.md).

If any of these clash with your project, exclude them at the dependency level.

## Local development against an unreleased change

If you're patching `acc-client` and want a downstream project to use your change before it's published:

```bash
# in acc-client checkout
./gradlew publishToMavenLocal

# in downstream project
repositories {
  mavenLocal()      // must come before mavenCentral / jitpack
  mavenCentral()
  maven("https://jitpack.io")
}

dependencies {
  implementation("com.github.prule:acc-client:main-SNAPSHOT")
}
```

This pattern also works for unreleased acc-messages changes — clone that repo separately and `publishToMavenLocal` it.

## Minimal usage example

```kotlin
import com.github.prule.acc.client.*

suspend fun main() {
  val context = ClientContext()
  AccClient(
    AccClientConfiguration(
      name = "MyClient",
      port = 9000,
      serverIp = "127.0.0.1",
      connectionPassword = "asd",
    )
  ).connect(
    listOf(
      LoggingListener(),
      ContextUpdater(context),
      SessionDetector(context, listOf(/* your SessionEventListeners */)),
    )
  )
}
```

For a richer walk-through see [Listeners.md](Listeners.md), [ListenerRecipes.md](ListenerRecipes.md), and [ClientContext.md](ClientContext.md).

## JDK requirement

Library is built with JDK 21 toolchain (`kotlin { jvmToolchain(21) }`). Consumers should also use JDK 21+ — older JDKs will fail with `UnsupportedClassVersionError` on classes that use newer JVM features, but the library itself targets a class-file version compatible with JDK 21.

## Kotlin version

Library is built with Kotlin 2.3.20. Consumers on older Kotlin versions may see compatibility issues with stdlib API. Recommend Kotlin 2.0+.

## License

See `LICENSE` in the repository root (or upstream at <https://github.com/prule/acc-client>).
