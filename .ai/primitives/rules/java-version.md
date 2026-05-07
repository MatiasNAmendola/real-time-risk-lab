---
name: java-version
applies_to: ["**/*.java", "**/pom.xml", "**/Dockerfile", "**/.java-version"]
priority: high
---

# Regla: java-version

## Mandatorio

- Java 25 LTS canonico. NO downgradearlo a 21 ni upgradear a 26 (26 no es LTS).
- `--release 25` en javac directo o `<maven.compiler.release>25</maven.compiler.release>` en pom.xml.
- En Dockerfile: `FROM eclipse-temurin:25-jre-alpine` o equivalente LTS.
- `.java-version` (si existe): debe contener `25`.

## Virtual Threads (Project Loom)

- Usar `Thread.ofVirtual().start(...)` o `Executors.newVirtualThreadPerTaskExecutor()` cuando aplique I/O bloqueante.
- En Vert.x 5: el event loop ya es non-blocking. Virtual threads aplican para workers o integraciones con libs bloqueantes (JDBC sync, etc.).
- No bloquear el event loop de Vert.x con operaciones largas; usar `vertx.executeBlocking()` si es necesario.

## Sealed Classes y Records

- Preferir `record` para Value Objects y DTOs (da equals/hashCode/toString gratis).
- Preferir `sealed interface` para tipos de dominio con alternativas finitas (ej. `RuleResult`).

## No usar

- `var` en contextos donde oscurece el tipo (APIs publicas, returns de metodos).
- Reflection sin documentacion clara del motivo.
- Clases de sun.misc o com.sun.* (no son parte del API publico).

## Verificacion

```bash
java -version  # debe mostrar 25.x
mvn -pl <module> compile  # debe pasar con --release 25
```
