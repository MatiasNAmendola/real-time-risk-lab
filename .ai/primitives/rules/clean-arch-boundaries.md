---
name: clean-arch-boundaries
applies_to: ["**/*.java"]
priority: high
---

# Regla: clean-arch-boundaries

## La regla de dependencia

Las dependencias en el codigo fuente solo pueden apuntar hacia adentro:

```
cmd/ config/
    |
infrastructure/  →  application/  →  domain/
                         |               |
                     (use cases)    (entities, rules)
```

- `domain/` no depende de nadie.
- `application/` solo depende de `domain/`.
- `infrastructure/` depende de `application/` y `domain/`.
- `cmd/` y `config/` dependen de todo (wiring).

## Puertos

- **Puerto de entrada (driving port)**: interfaz en `domain/usecase/` o `application/usecase/<aggregate>/`. Implementada en `application/usecase/<aggregate>/`.
- **Puerto de salida (driven port)**: interfaz en `domain/repository/`. Implementada en `infrastructure/repository/`.

## Violaciones comunes a evitar

| Violacion | Sintoma | Fix |
|---|---|---|
| Entidad de dominio con `JsonObject` | `domain/entity/Transaction.java` importa `io.vertx.core.json` | Crear mapper en `application/mapper/` |
| Use case con SQL | `application/usecase/...` tiene queries JDBC | Mover query a `infrastructure/repository/` |
| Controller con regla de negocio | Handler decide si aprobar/rechazar | Mover logica a `domain/rule/` |
| Repository con logica de negocio | `infrastructure/repository/` tiene cálculos de riesgo | Mover a `domain/service/` |

## Verificacion automatica

```bash
# domain/ no puede importar infrastructure/ ni application/
echo "Checking domain boundaries..."
if grep -r "import.*\.infrastructure\." poc/*/src/main/java/*/domain/ 2>/dev/null; then
    echo "BOUNDARY VIOLATION: domain imports infrastructure"
    exit 1
fi
if grep -r "import.*\.application\." poc/*/src/main/java/*/domain/ 2>/dev/null; then
    echo "BOUNDARY VIOLATION: domain imports application"
    exit 1
fi
echo "Boundaries OK"
```

## Design principle

Si te preguntan "como pruebas que es Clean Architecture": mostrar que los unit tests de `domain/` y `application/` no tienen dependencias de Vert.x, Postgres, ni Kafka en el classpath. Solo JUnit 5 y la aplicacion.
