# air-pollution-tracker

Java 17 multi-module Maven project: air pollution microservices.

## Modules

- `pollution-common` — shared utilities; every service depends on it.
- `pollution-data-collector` — service that collects pollution data and publishes it.
- `pollution-data-writer` — service that writes pollution data.

## Configuration rules

- Every microservice module MUST have a `config` folder — the package `com.pollution.<service>.config` — containing a `Config.java` class that centralizes that service's configuration.
- All of a service's settings (service name, endpoints, paths, tuning values) live on its `Config` class as constants or static accessors. The rest of the service reads configuration only through `Config` — never via scattered `System.getenv(...)` / `System.getProperty(...)` calls.

## Messaging rules

- Services talk to each other only through the `IPublisher<T>`/`ISubscriber<T>` interfaces from `com.pollution.common.pubsub`. A service must never know it is talking to Kafka: no `org.apache.kafka.*` or `com.pollution.common.pubsub.kafka.*` imports anywhere in a service module's business code.
- The concrete implementation (`KafkaPublisher`/`KafkaSubscriber`) is named and instantiated in exactly one place per service — its wiring/startup code (the `config` package) — and handed to the rest of the service as the interface type.

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
