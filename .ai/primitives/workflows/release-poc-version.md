---
name: release-poc-version
description: Crear una version etiquetada de una PoC con changelog y commit limpio
steps: [changelog, version-bump, tag, push]
---

# Workflow: release-poc-version

## Cuando usar

Cuando una PoC alcanza un hito demostrable (ej. "todos los patrones de comunicacion implementados").

## Pasos

### 1. Verificar estado limpio

```bash
git status  # debe estar limpio
./gradlew test -pl poc/<poc-name>/  # tests verdes
```

### 2. Actualizar version en build.gradle.kts

```xml
<!-- De: -->
<version>1.0.0-SNAPSHOT</version>
<!-- A: -->
<version>1.0.0</version>
```

```bash
./gradlew versions:set -DnewVersion=1.0.0 -pl poc/<poc-name>/ -DgenerateBackupPoms=false
```

### 3. Actualizar README con estado final

Usar skill `update-poc-readme`. Marcar features completadas en el checklist.

### 4. Commit de release

```bash
git add poc/<poc-name>/
git commit -m "release(poc/<poc-name>): v1.0.0 — <resumen de lo que incluye>"
```

### 5. Tag

```bash
git tag poc/<poc-name>/v1.0.0
```

### 6. Volver a SNAPSHOT para desarrollo

```bash
./gradlew versions:set -DnewVersion=1.1.0-SNAPSHOT -pl poc/<poc-name>/ -DgenerateBackupPoms=false
git add poc/<poc-name>/build.gradle.kts
git commit -m "chore(poc/<poc-name>): bump to 1.1.0-SNAPSHOT"
```

### 7. Actualizar inventario

`.ai/context/poc-inventory.md`: actualizar columna Estado con la version liberada.

## Notas

- Los tags siguen formato: `poc/<poc-name>/v<semver>`.
- No hacer release con tests en rojo.
- La version 1.0.0 implica que la PoC es "demostrable en review tecnica".
