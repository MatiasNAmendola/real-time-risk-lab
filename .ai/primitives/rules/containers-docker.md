---
name: containers-docker
applies_to: ["**/Dockerfile", "**/docker-compose*.yml", "**/Chart.yaml", "**/values*.yaml"]
priority: medium
---

# Regla: containers-docker

## Dockerfile

- Multi-stage build obligatorio: stage `build` con JDK, stage final con JRE slim.
- Base image final: `eclipse-temurin:25-jre-alpine` (o equivalente Alpine).
- Non-root user obligatorio:
  ```dockerfile
  RUN addgroup -S appgroup && adduser -S appuser -G appgroup
  USER appuser
  ```
- Healthcheck obligatorio:
  ```dockerfile
  HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8080/healthz || exit 1
  ```
- No copiar `.git/`, `build/` completo, o secretos al imagen.
- WORKDIR explícito: `WORKDIR /app`.

## docker-compose

- Versiones de imagen explícitas (no `:latest` para servicios core).
- Healthchecks en servicios con `depends_on: condition: service_healthy`.
- Redes nombradas, no la red default.
- Volumes nombrados para datos persistentes.

## k8s / Helm

- `resources.requests` y `resources.limits` siempre definidos.
- `readinessProbe` y `livenessProbe` configurados.
- `securityContext.runAsNonRoot: true`.
- `securityContext.readOnlyRootFilesystem: true` donde sea posible.

## Verificacion

```bash
docker build --no-cache -t risk-engine:test .
docker run --rm risk-engine:test whoami  # no debe ser root
```

## No permitido

- `FROM openjdk:*` (deprecated). Usar `eclipse-temurin`.
- Secrets en variables de entorno en el Dockerfile o docker-compose con valores reales.
- `--privileged` en contenedores de la aplicacion.
