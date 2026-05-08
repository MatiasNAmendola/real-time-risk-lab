---
name: update-poc-readme
intent: Actualizar el README de una PoC para reflejar el estado actual, stack, y como correrla
inputs: [poc_path, new_features, current_status]
preconditions:
  - poc/<name>/README.md existe
postconditions:
  - README actualizado con: proposito, como correr, stack con versiones, estado, demos disponibles
  - poc-inventory.md en .ai/context/ actualizado
related_rules: [naming-conventions]
---

# Skill: update-poc-readme

## Estructura del README

```markdown
# <Nombre PoC> — Real-Time Risk Lab

<Descripcion de 2 lineas: que demuestra esta PoC y por que existe>

---

## Que demuestra

- <patron 1>
- <patron 2>
- <patron 3>

---

## Stack

| Componente | Version |
|---|---|
| Java | 21 LTS baseline (`--release 21`); Java 25 objetivo documentado |
| Vert.x | 5.0.12 |
| ... | ... |

---

## Estructura

<tree simplificado del layout de paquetes>

---

## Como correr

### Prerequisitos
- Docker Desktop / OrbStack corriendo
- JDK 21+ en PATH (`java -version`); Java 25 es opcional
- Gradle 3.9+ (`./gradlew --version`)

### Levantar dependencias
```bash
docker-compose up -d
```

### Compilar y correr
```bash
./gradlew shadowJar
./scripts/run.sh
```

### Verificar
```bash
curl http://localhost:8080/healthz
./scripts/test.sh
```

---

## Estado

- [x] Clean Architecture layout
- [x] REST endpoint
- [ ] SSE streaming (en progreso)
- [ ] ATDD completo

---

## Demos en vivo

1. <Mostrar X>: `curl ...`
2. <Mostrar Y>: `./scripts/demo.sh`
```

## Pasos

1. Leer el README actual.
2. Actualizar secciones desactualizadas.
3. Actualizar `.ai/context/poc-inventory.md` con estado nuevo.
4. Commit: `docs(poc/<name>): update README with current state`.
