---
name: new-poc-bootstrap
description: Como arrancar una nueva PoC siguiendo todas las convenciones del repo
steps: [create-dir, pom, layout, readme, scripts, docker-compose, git, inventory]
---

# Workflow: new-poc-bootstrap

## Cuando usar

Cuando el roadmap (docs/03-poc-roadmap.md) indica una nueva PoC o cuando se decide demostrar un patron que no esta cubierto.

## Pasos

### 1. Decidir el nombre y proposito

- Nombre: kebab-case descriptivo. Ejemplos: `java-ml-integration`, `java-cqrs-demo`.
- Proposito: una frase. Que patron demuestra especificamente.
- Confirmar que no existe algo similar en `poc/`.

### 2. Usar skill bootstrap-new-poc

Ver `.ai/primitives/skills/bootstrap-new-poc.md` para el detalle.

```bash
mkdir -p poc/<poc-name>/{src/main/java/com/naranjax/interview/<domain>,src/test,scripts}
```

### 3. pom.xml con Java 25 y Vert.x 5.0.12

Copiar de `poc/java-vertx-distributed/pom.xml` como base. Ajustar `artifactId`.

### 4. Layout canonico

```
src/main/java/com/naranjax/interview/<domain>/
├── domain/{entity,repository,usecase,service,rule}
├── application/{usecase/<aggregate>,mapper,dto}
├── infrastructure/{controller,consumer,repository,resilience,time}
├── config/
└── cmd/
```

### 5. README.md

Usar skill `update-poc-readme`. Incluir: proposito, stack, como correr, estado inicial.

### 6. scripts/

- `run.sh`: arranca la app.
- `test.sh`: corre tests.
- `down.sh`: para dependencias.
Todos deben ser ejecutables (`chmod +x scripts/*.sh`).

### 7. docker-compose.yml

Solo los servicios necesarios para este PoC. Versiones exactas (ver `.ai/context/stack.md`).

### 8. .gitignore local

```
target/
*.class
.env.local
```

### 9. Commit inicial

```bash
git add poc/<poc-name>/
git commit -m "feat(poc): bootstrap <poc-name> — <proposito>"
```

### 10. Actualizar inventario

Agregar a `.ai/context/poc-inventory.md` con estado "en progreso".

## Checklist

- [ ] Directorio bajo `poc/` (no en `tests/` ni `cli/`)
- [ ] pom.xml con Java 25, maven.compiler.release=25
- [ ] Layout canonico completo
- [ ] README.md con proposito y como correr
- [ ] scripts/ ejecutables
- [ ] poc-inventory.md actualizado
- [ ] Commit con mensaje convencional
