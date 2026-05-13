# Car Models

Reference for [`CarModelRepository`](../acc-client-core/src/main/kotlin/com/github/prule/acc/client/CarModelRepository.kt) and the bundled `car_model_type.csv` lookup.

## What it is

`ENTRY_LIST_CAR` reports a car as a numeric `carModelType` (e.g. `1`). To turn that into a human-readable name (e.g. `"Mercedes-AMG GT3"`), the library ships a CSV lookup file and a small repository that reads it.

```kotlin
val repo = CarModelRepository()
val model = repo.findById(1)
// CarModel(id=1, name="Mercedes-AMG GT3")
```

`findById` returns `null` if the id isn't in the CSV — handle accordingly (most likely the CSV needs an entry; see [Extending](#extending)).

## API

```kotlin
class CarModelRepository {
  fun findById(id: Int): CarModel?
}

data class CarModel(val id: Int, val name: String)
```

The CSV is loaded lazily on first `findById` call and cached for the lifetime of the `CarModelRepository` instance. Thread-safe for concurrent reads after the first call.

## CSV location

Bundled as a resource: `acc-client-core/src/main/resources/com/github/prule/acc/client/car_model_type.csv`.

Format: standard CSV with header.

```csv
id,name
0,"Porsche 991 GT3 R"
1,"Mercedes-AMG GT3"
2,"Ferrari 488 GT3"
...
```

Quoting on the `name` column is required if the name contains a comma. The library uses `kotlin-csv-jvm` which handles standard CSV escaping.

## Coverage

The shipped file currently includes (as of this writing):

| Range | Class |
|---|---|
| 0–32 | GT3 (Porsche 991, Mercedes-AMG, Ferrari 488, Audi R8, Lamborghini Huracan, McLaren 650S/720S, Nissan GT-R, BMW M6/M4, Bentley Continental, Honda NSX, Lexus RC F, Aston Martin V12/Vantage, Reiter Engineering R-EX, Emil Frey Jaguar, Lamborghini Huracan Evo, Porsche 911 II, Mercedes-AMG GT3 Evo, Ferrari 488 Evo, Mercedes-AMG GT3 2020, BMW M4 GT3, Audi R8 LMS GT3 Evo II, etc.) |
| 50–61 | GT4 (Alpine, Aston Martin, Audi, BMW, Chevrolet, Ginetta, Ktm, Maserati, McLaren, Mercedes, Porsche) |
| 80–86 | GT2 (Audi R8, Ktm Xbow, Maserati MC20, Mercedes AMG, Porsche 911 GT2 RS, Porsche 935) |

ACC adds new cars in updates — if you see `findById(N) == null` for a new car, the CSV needs that entry.

## Extending

If you race a class or DLC car not in the bundled file, you can either:

### Option 1 — patch the bundled file in your fork

Edit `acc-client-core/src/main/resources/com/github/prule/acc/client/car_model_type.csv`, add the row, rebuild, publish locally:

```bash
./gradlew publishToMavenLocal
```

Open a PR upstream once you've confirmed the new ids — others will benefit. ACC's car ids are stable across patches (Kunos doesn't renumber existing cars).

### Option 2 — replace the repository in your app

`CarModelRepository` is small. Subclass or replace it with your own that reads from a different source — e.g. a JSON file you maintain alongside your application:

```kotlin
class MyCarRepo {
  private val map: Map<Int, String> = loadFromMyOwnFile()
  fun findById(id: Int): CarModel? = map[id]?.let { CarModel(id, it) }
}
```

The library doesn't itself call `CarModelRepository` — it's a convenience for consumer code. So bringing your own implementation has no compatibility implications.

## Finding new car ids

Two methods:

1. **From a recording.** Capture an `ENTRY_LIST_CAR` for the unknown car (the JSON column shows `"carModelType":N`) and look up `N` against your existing CSV.
2. **From acc-messages docs.** The Kunos-published broadcasting protocol docs occasionally list new ids. The acc-messages repo updates the Kaitai schema when those become available.

## Related

- [`CarEntry.carModelType`](../acc-client-core/src/main/kotlin/com/github/prule/acc/client/CarEntry.kt) — the int you'd pass to `findById`.
- [`ClientContext.cars`](ClientContext.md) — the map of `carId → CarEntry` populated by `ContextUpdater`. Each entry has the `carModelType` field.
