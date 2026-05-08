---
name: architecture-clean
applies_to: ["**/src/main/java/**/*.java", "**/build.gradle.kts"]
priority: high
---

# Regla: architecture-clean

## Layout canonico (estilo enterprise Go)

Todo modulo Java del repo DEBE seguir este layout de paquetes:

```
io.riskplatform.<domain>/
├── domain/
│   ├── entity/          # Entidades con identidad
│   ├── repository/      # Interfaces de puertos de salida (no implementaciones)
│   ├── usecase/         # Interfaces de puertos de entrada (no implementaciones)
│   ├── service/         # Servicios de dominio (logica sin infraestructura)
│   └── rule/            # Reglas de negocio puras
├── application/
│   ├── usecase/<aggregate>/   # Implementaciones de use cases
│   ├── mapper/                # Mappers dto<->dominio
│   └── dto/                   # Request/Response DTOs
├── infrastructure/
│   ├── controller/      # HTTP handlers, Router setup
│   ├── consumer/        # Kafka/Redpanda consumers
│   ├── repository/      # Implementaciones de puertos de salida
│   ├── resilience/      # Circuit breakers, bulkheads
│   └── time/            # Clock, TimeProvider
├── config/              # Wiring, configuracion, factories
└── cmd/                 # Entry points (main), verticles
```

## Invariantes

1. `domain/` NO importa de `application/` ni de `infrastructure/`.
2. `application/` NO importa de `infrastructure/`.
3. `infrastructure/` puede importar de `application/` y `domain/`.
4. `config/` y `cmd/` pueden importar de todo.

## No permitido

- Logica de negocio en `infrastructure/controller/` o `infrastructure/consumer/`.
- Instancias de `JsonObject`, `RowSet`, `KafkaConsumerRecord` en `domain/`.
- Annotations de frameworks (Spring, Quarkus) en `domain/` o `application/`.
- Acceso a static state global desde `domain/` o `application/`.

## Verificacion de boundaries

```bash
# Verificar que domain/ no importa de infrastructure/
grep -r "import.*\.infrastructure\." poc/*/src/main/java/*/domain/ && echo "VIOLATION FOUND"
grep -r "import.*\.application\." poc/*/src/main/java/*/domain/ && echo "VIOLATION FOUND"
```
