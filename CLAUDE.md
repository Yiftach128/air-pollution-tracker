# air-pollution-tracker

Java 17 multi-module Maven project: air pollution microservices.

## Modules

- `pollution-common` — shared utilities; every service depends on it.
- `persistence-common` — the shared cache abstraction (`IPollutionCache<T>`) and its Redis implementation; depends on `pollution-common`. Services that keep state depend on it.
- `pollution-data-collector` — service that collects pollution data and publishes it.
- `pollution-data-writer` — service that writes pollution data.
- `pollution-data-analyzer` — service that analyzes pollution data.

## Configuration rules

- Every microservice module MUST have a `config` folder — the package `com.pollution.<service>.config` — containing two classes:
  - `Config.java` — values only: all of a service's settings (service name, endpoints, paths, tuning values) as constants or static accessors. The rest of the service reads configuration only through `Config`.
  - `Wiring.java` — object assembly only: the `create...()` factories that build the service's object graph from `Config` values. This is the one place per service that names concrete implementations; everything it returns is typed as an interface, and `main` calls `Wiring`, never constructors.
- Environment variables are read only through `com.pollution.common.config.Env` (`Env.getLong("NAME", default)` etc.), and only from a `Config` class. No `System.getenv(...)` / `System.getProperty(...)` anywhere else. Each service declares its own variable names and defaults — `Env` is the mechanism, not a shared value store.
- Services do not read `Config` from business classes; `Wiring` passes settings in through constructors (e.g. the collector's poll interval), so classes stay testable without environment setup.

## Messaging rules

- Services talk to each other only through the `IPublisher<T>`/`ISubscriber<T>` interfaces from `com.pollution.common.pubsub`. A service must never know it is talking to Kafka: no `org.apache.kafka.*` or `com.pollution.common.pubsub.kafka.*` imports anywhere in a service module's business code.
- The concrete implementation (`KafkaPublisher`/`KafkaSubscriber`) is named and instantiated in exactly one place per service — its `config/Wiring.java` — and handed to the rest of the service as the interface type.

## Persistence rules

- Services keep durable state only through the `IPollutionCache<T>` interface from `com.pollution.persistence` (`persistence-common`): a typed object cache over string keys — `setObjectValue`, `getObjectValue`, `getObjectKeysByPattern`, `getObjectValuesByKeys`, `getObjectValuesByPattern`, `removeObject` — with values stored as JSON via `JsonSupport`. A service must never know it is talking to Redis: no `redis.clients.*` or `com.pollution.persistence.redis.*` imports anywhere in a service module's business code.
- Keys are global to the store, so each service owns a key **prefix** constant in its own `Config` (`<service>:<concept>:`, e.g. `analyzer:rolling-average-state:`), only ever reads and writes keys under it, and finds its objects with the pattern `<prefix>*`.
- The concrete implementation (`RedisPollutionCache<T>`, one instance per value type, built with the `Class<T>`) is named and instantiated only in a service's `config/Wiring.java`. Redis connection settings (`REDIS_HOST`/`REDIS_PORT`) live in `com.pollution.persistence.config.Config`.
- A service wraps the cache behind a domain-specific store interface in its `persistence/` package (e.g. the analyzer's `IRollingAverageStateStore`), so business code speaks in domain terms (save this sensor's state) rather than keys and values.

## Package layout rules

- Entities — the data types the system is about (messages, readings, sensors, enums of domain values) — live in an `entities` package: `com.pollution.common.entities` for types shared across services (`AbstractMessage`, `Pollutant`, `PollutionData`, `PollutionAverage` with its per-window `WindowAverage`, `PollutionAlert`) and `com.pollution.<service>.entities` for service-specific ones. Entities are immutable value types (records/enums, or final classes with final fields and value `equals`/`hashCode`) with no I/O and no dependencies on APIs, Kafka, or config — no Jackson annotations either. Messages extend the abstract class `AbstractMessage` (which holds `city` and `timestamp`) and expose exactly one public constructor whose parameter names match the field names: that is how `JsonSupport` in `pollution-common` rebuilds them from JSON (the build keeps parameter names via `javac -parameters`).
- Services are organized **by layer, then by provider**. Each layer is a top-level package holding only provider-agnostic types (interfaces, shared helpers); everything specific to a data provider goes in a sub-package named after it (`<layer>/purpleair`). The collector's layers:
  - `entities/` — `entities/purpleair/` holds `PurpleAirSensor`, `PurpleAirReading`, `PurpleAirSensorInfo`.
  - `api/` — access to external APIs: `IApiKeyProvider`, `RoundRobinApiKeyProvider`; `api/purpleair/` holds `PurpleAirSensorApi`, `PurpleAirApiException`.
  - `registry/` — which sensors we follow and their metadata: `ISensorRegistry<S, I>`; `registry/purpleair/` holds `PurpleAirSensorRegistry`.
  - `fetchers/` — turning provider data into `PollutionData`: `IReadingsFetcher`; `fetchers/purpleair/` holds `PurpleAirReadingsFetcher`.
- Layers depend downward only: `fetchers → registry → api → entities`. A layer's top-level package never imports from a provider sub-package, and a provider sub-package never imports from a *sibling layer's* provider sub-package except along that same direction. Provider-agnostic interfaces must not mention provider types — generify them (as `ISensorRegistry<S, I>`) rather than leak `PurpleAir*` into a top-level package.
- If a class would work unchanged for another provider, it does not belong in a provider sub-package.
- The analyzer's layers:
  - `entities/` — `Reading`, `SensorPollutant` (the identity of a series: one sensor's readings of one pollutant), `RollingAverageState`.
  - `analysis/` — `RollingAverage`: one rolling window of one series; produces and restores from `RollingAverageState`. `RollingAverageGroup`: all of a series' windows (default 10 min, 60 min, 24 h — `Config.getRollingAverageWindows()`, one `RollingAverage` each, ascending), fed the same readings; hands out the longest window's state for persistence and the `List<WindowAverage>` for publishing.
  - `persistence/` — durable per-series state storage: `IRollingAverageStateStore`; `CacheBackedRollingAverageStateStore` keeps one object per series in an `IPollutionCache<RollingAverageState>` under `Config.ROLLING_AVERAGE_STATE_KEY_PREFIX + sensorId + ":" + pollutant` (provider-agnostic — the concrete cache is chosen in `Wiring`); `LoggingRollingAverageStateStore` is a no-persistence stand-in for running without a cache.
  - Direction: `PollutionDataAnalyzerService → persistence, analysis → entities`.
  - Persistence model: only a series' **longest** window is persisted, saved (overwriting the previous state) after every reading that changes the series. On startup every series is loaded once and each shorter window is rebuilt from the longest one's state by replaying its readings through `RollingAverage.loadFromPersistence`, which drops those outside the shorter window. There is no periodic flush.
  - Publishing: after every reading that changes a series, one `PollutionAverage` (city, source, pollutant, one `WindowAverage` per window, timestamp = the newest reading) goes to `POLLUTION_AVERAGE_TOPIC`, keyed by source.
- Every entity gets its own `.java` file — never nest one entity record inside another.
- JSON outside Kafka (persistence, rolling-average state) goes through `com.pollution.common.json.JsonSupport` (`toJson`/`fromJson`, throwing the unchecked `JsonException`). It holds the single shared Jackson configuration; the Kafka serializers use it too, so nothing else in the project may build its own `ObjectMapper`.

## Design rules

- Follow SOLID. Aim for high cohesion and low coupling: each class/module has one reason to change, and dependencies point at abstractions, never at concrete implementations. The messaging rules above are an instance of this — services depend on `IPublisher`/`ISubscriber`, not on Kafka.
- Callers must be able to use an implementation entirely through its interface — never design so that a caller has to downcast to the concrete class. Any public method callers need, lifecycle included, belongs on the interface.
- If implementations hold resources that need releasing (connections, threads, clients), put the lifecycle on the interface: make it `extends AutoCloseable` and redeclare `void close();` (dropping the checked `throws Exception`) so callers get try-with-resources through the abstraction. See `IPublisher`/`ISubscriber` in `pollution-common` for the pattern.

## Logging rules

- All output in this project MUST go through the SLF4J logger — never use `System.out.println`, `System.err.println`, `printStackTrace()`, or `java.util.logging`.
- Always work with the actual SLF4J `Logger`, obtained via `PollutionLogger.getLogger(...)` from `pollution-common`.
- The logger variable must always be named `logger`:

  ```java
  private static final Logger logger = PollutionLogger.getLogger(MyClass.class);
  ```

- Use parameterized messages (`logger.info("wrote {} records", count)`), not string concatenation.
- Logback is the logging engine. The single shared `logback.xml` lives in `pollution-common/src/main/resources` and applies to all services via the classpath. Services must NOT add their own `logback.xml` — change the shared one instead.
