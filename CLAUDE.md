# air-pollution-tracker

Java 17 multi-module Maven project: air pollution microservices.

## Modules

- `pollution-common` — shared utilities; every service depends on it.
- `pollution-data-writer` — service that writes pollution data.

## Logging rules

- All output in this project MUST go through the SLF4J logger — never use `System.out.println`, `System.err.println`, `printStackTrace()`, or `java.util.logging`.
- Always work with the actual SLF4J `Logger`, obtained via `PollutionLogger.getLogger(...)` from `pollution-common`.
- The logger variable must always be named `logger`:

  ```java
  private static final Logger logger = PollutionLogger.getLogger(MyClass.class);
  ```

- Use parameterized messages (`logger.info("wrote {} records", count)`), not string concatenation.
- Logback is the logging engine. The single shared `logback.xml` lives in `pollution-common/src/main/resources` and applies to all services via the classpath. Services must NOT add their own `logback.xml` — change the shared one instead.
