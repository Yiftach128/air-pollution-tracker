# air-pollution-tracker

Java 17 multi-module Maven project: air pollution microservices.

## Modules

- `pollution-common` — shared utilities; every service depends on it.
- `pollution-data-collector` — service that collects pollution data and publishes it.
- `pollution-data-writer` — service that writes pollution data.

## Configuration rules

- Every microservice module MUST have a `config` folder — the package `com.pollution.<service>.config` — containing two classes:
  - `Config.java` — values only: all of a service's settings (service name, endpoints, paths, tuning values) as constants or static accessors. The rest of the service reads configuration only through `Config`.
  - `Wiring.java` — object assembly only: the `create...()` factories that build the service's object graph from `Config` values. This is the one place per service that names concrete implementations; everything it returns is typed as an interface, and `main` calls `Wiring`, never constructors.
- Environment variables are read only through `com.pollution.common.config.Env` (`Env.getLong("NAME", default)` etc.), and only from a `Config` class. No `System.getenv(...)` / `System.getProperty(...)` anywhere else. Each service declares its own variable names and defaults — `Env` is the mechanism, not a shared value store.
- Services do not read `Config` from business classes; `Wiring` passes settings in through constructors (e.g. the collector's poll interval), so classes stay testable without environment setup.

## Messaging rules

- Services talk to each other only through the `IPublisher<T>`/`ISubscriber<T>` interfaces from `com.pollution.common.pubsub`. A service must never know it is talking to Kafka: no `org.apache.kafka.*` or `com.pollution.common.pubsub.kafka.*` imports anywhere in a service module's business code.
- The concrete implementation (`KafkaPublisher`/`KafkaSubscriber`) is named and instantiated in exactly one place per service — its `config/Wiring.java` — and handed to the rest of the service as the interface type.

## Package layout rules

- Entities — the data types the system is about (messages, readings, sensors, enums of domain values) — live in an `entities` package: `com.pollution.common.entities` for types shared across services (`IMessage`, `Pollutant`, `PollutionData`, `PollutionAverage`, `PollutionAlert`) and `com.pollution.<service>.entities` for service-specific ones. Entities are immutable value types (records/enums) with no I/O and no dependencies on APIs, Kafka, or config.
- Services are organized **by layer, then by provider**. Each layer is a top-level package holding only provider-agnostic types (interfaces, shared helpers); everything specific to a data provider goes in a sub-package named after it (`<layer>/purpleair`). The collector's layers:
  - `entities/` — `entities/purpleair/` holds `PurpleAirSensor`, `PurpleAirReading`, `PurpleAirSensorInfo`.
  - `api/` — access to external APIs: `IApiKeyProvider`, `RoundRobinApiKeyProvider`; `api/purpleair/` holds `PurpleAirSensorApi`, `PurpleAirApiException`.
  - `registry/` — which sensors we follow and their metadata: `ISensorRegistry<S, I>`; `registry/purpleair/` holds `PurpleAirSensorRegistry`.
  - `fetchers/` — turning provider data into `PollutionData`: `IReadingsFetcher`; `fetchers/purpleair/` holds `PurpleAirReadingsFetcher`.
- Layers depend downward only: `fetchers → registry → api → entities`. A layer's top-level package never imports from a provider sub-package, and a provider sub-package never imports from a *sibling layer's* provider sub-package except along that same direction. Provider-agnostic interfaces must not mention provider types — generify them (as `ISensorRegistry<S, I>`) rather than leak `PurpleAir*` into a top-level package.
- If a class would work unchanged for another provider, it does not belong in a provider sub-package.

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
