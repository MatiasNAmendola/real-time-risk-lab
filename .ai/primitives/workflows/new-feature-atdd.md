---
name: new-feature-atdd
description: Full ATDD cycle para agregar un feature al risk engine — feature file → red → impl → green → demo
steps: [write-feature, run-red, implement, run-green, document]
---

# Workflow: new-feature-atdd

## Cuando usar

Cada vez que se agrega un nuevo comportamiento observable al sistema (endpoint, regla, patron de comunicacion).

## Pasos

### 1. Escribir el feature file (RED)

Antes de escribir una sola linea de codigo de produccion:

```gherkin
# poc/java-vertx-distributed/atdd-tests/src/test/resources/features/<feature>.feature
Feature: <comportamiento>

  Scenario: happy path
    Given <precondicion>
    When <accion>
    Then <resultado esperado>

  Scenario: error case
    Given <precondicion>
    When <accion invalida>
    Then <error esperado>
```

### 2. Correr → debe fallar (RED confirmado)

```bash
./gradlew :poc:java-vertx-distributed:atdd-tests:test -Patdd -Dtest=KarateRunner
# Expected: BUILD FAILURE con "scenario failed"
```

Si el test pasa sin implementar: el test esta mal escrito.

### 3. Implementar el minimo para que pase

Seguir en orden:
1. Si es nuevo endpoint: usar skill `add-rest-endpoint` (o el patron correspondiente).
2. Si es nueva regla: usar skill `add-fraud-rule`.
3. Si es nuevo patron de comunicacion: usar skill del patron.
4. Mantener clean boundaries (rule `clean-arch-boundaries`).
5. Agregar OTEL (rule `observability-otel`).

### 4. Correr → debe pasar (GREEN)

```bash
./gradlew :poc:java-vertx-distributed:atdd-tests:test -Patdd
# Expected: BUILD SUCCESS
```

### 5. Refactorizar (si aplica)

- Eliminar duplicacion.
- Mejorar nombres.
- Verificar que tests siguen verdes.

### 6. Documentar y commitear

```bash
# Verificar coverage
./gradlew :<module-path>:test :<module-path>:jacocoTestReport

# Commit
git add poc/ tests/ .ai/context/
git commit -m "feat(<scope>): <descripcion del comportamiento en una linea>"
```

### 7. Actualizar exploration-state.md

Marcar la feature como completada en `.ai/context/exploration-state.md`.

## Checklist

- [ ] Feature file escrito antes de implementar
- [ ] Test fallaba antes de implementar (RED confirmado)
- [ ] Test pasa despues de implementar (GREEN)
- [ ] Clean Architecture boundaries no violados
- [ ] correlationId propagado
- [ ] OTEL span/metric agregado
- [ ] Coverage >= 80%
- [ ] exploration-state.md actualizado
