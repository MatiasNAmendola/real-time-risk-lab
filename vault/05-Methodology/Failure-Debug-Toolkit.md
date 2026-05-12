---
title: Failure Debug Toolkit — herramientas de diagnóstico de fallas
tags: [methodology/debugging, tooling, failures, diagnostics, ai-assisted]
created: 2026-05-12
source_archive: docs/32-failure-debug-toolkit.md (migrado 2026-05-12)
---

# Failure Debug Toolkit — herramientas de diagnóstico de fallas

## Por qué este bundle

Los sistemas distribuidos fallan en capas: una sola falla de test puede tener su root cause en una mala configuración de red Docker, un memory limit demasiado bajo o una regresión genuina en lógica de negocio. Sin tooling estructurado, diagnosticar qué capa es responsable consume más tiempo que el fix mismo.

Este bundle provee tres herramientas complementarias:

1. **Failures aggregator** — vista unificada de fallas a través de todas las suites de tests.
2. **Auto-fix dispatcher** — clusteriza fallas por causa y genera prompts de fix accionables para revisión humana (nunca aplica cambios automáticamente).
3. **Comandos de debug** — investigación a nivel servicio: logs, diagnóstico heurístico, snapshots forenses, lookup de tracing distribuido y probes de DNS.

Las tres están cableadas en `./nx` para un único entry point.

---

## 2. Failures aggregator

**Script:** `.ai/scripts/failures-aggregator.py`

### Formatos soportados

| Formato | Patrón de ubicación |
|---|---|
| Surefire XML de Gradle | `**/build/test-results/test/*.xml` |
| Cucumber JSON | `tests/**/build/cucumber-reports/report.json` |
| JSON de summary de Karate | `poc/*/atdd-tests/build/karate-reports/karate-summary.json` |
| Smoke runner | `out/smoke/latest/meta.json` o `checks/*.md` |

### Heurísticas de root cause

| Patrón | Root cause |
|---|---|
| `Connection refused`, `EHOSTUNREACH` | servicio caído |
| `Timeout`, `Timed out` | servicio lento |
| `NXDOMAIN`, `UnknownHostException` | problema de red/DNS |
| `AssertionError` | problema de lógica de test |
| `OutOfMemoryError` | mem_limit demasiado bajo |
| `ClassNotFoundException`, `NoClassDefFoundError` | problema de classpath |

### CLI

```bash
./nx failures                          # escanea todas las suites
./nx failures --suite atdd-karate      # solo una suite
./nx failures --json                   # output machine-readable
./nx failures --since 1h               # solo resultados recientes
```

---

## 3. Auto-fix dispatcher

**Script:** `.ai/scripts/auto-fix-dispatcher.py`

El dispatcher lee el output de fallas, clusteriza por root cause y genera un template de prompt por cluster. **El dispatcher nunca aplica cambios.** Solo los propone.

### Safeguards (hardcoded, no negociables)

```python
FORBIDDEN_FIX_PATTERNS = [
    r"@(Disabled|Ignore|Skip|wip)",     # nunca deshabilitar tests
    r"\.skip\s*\(",                      # nunca .skip()
    r"//\s*assert\s",                   # nunca comentar assertions
    r"coverage.*=\s*[0-9]+\s*[#//]",   # nunca bajar thresholds de coverage
]
MAX_FILES_PER_FIX = 5
```

### CLI

```bash
./nx test --auto-fix --dry-run         # muestra plan, sin archivos de output
./nx test --auto-fix --apply           # confirma interactivamente por cluster
```

---

## 4. Comandos de debug

**Script:** `.ai/scripts/debug-runner.py`

### logs

```bash
./nx logs                              # todos los servicios
./nx logs --service controller-app
./nx logs --since 5m
./nx logs --grep ERROR
./nx logs --correlation-id abc-123
```

### diagnose

```bash
./nx debug diagnose
```

Corre heurísticas sin ningún LLM:

1. `docker compose ps -a` — estado del servicio, exit codes, health.
2. Scan de patrones de log — NXDOMAIN, OOM, Connection refused, Timeout, panic.
3. Presión de recursos — `docker stats --no-stream`, marca contenedores >80% CPU o >90% mem.
4. Cross-probe de DNS — `getent hosts` entre pares de servicios.
5. Sanity de OpenObserve — consulta `/api/default/_search`.

### snapshot

```bash
./nx debug snapshot
```

Escribe `out/debug/<ts>/`: compose-ps, docker-stats, logs por servicio, host-info, openobserve-summary.

### trace

```bash
./nx debug trace abc-123
```

Consulta OpenObserve por trace ID, imprime el timeline de spans entre servicios.

### probe

```bash
./nx debug probe
```

Corre `getent hosts` desde cada servicio Java a todos los demás. Reporta NXDOMAIN al instante y sugiere fixes de configuración de red.

---

## 5. Cuándo usar cada herramienta

| Situación | Herramienta |
|---|---|
| El pipeline de CI muestra fallas, hace falta un summary | `./nx failures` |
| Quiero sugerencias de fix asistidas por IA | `./nx test --auto-fix --dry-run` |
| Servicio no responde después de un deploy | `./nx debug diagnose` |
| Hace falta compartir un bug report con contexto | `./nx debug snapshot` |
| Investigando un request específico de usuario | `./nx debug trace <id>` |
| Llamadas inter-servicio fallando | `./nx debug probe` |

---

## 6. Anti-patrones

### Modo autónomo como default

Correr `--autonomous` en pipelines de CI significa que un agente puede modificar código productivo sin ningún humano en el loop.

### Tratar al "suggested fix" como autoritativo

Las heurísticas son basadas en patrones. Leer el error real antes de aplicar cualquier fix.

### Modificar assertions para que los tests pasen

Es el anti-patrón más peligroso. Una assertion que falla es una señal. El `FORBIDDEN_FIX_PATTERNS` del dispatcher enforza esto a nivel tooling.

---

## 7. Principio de diseño clave

> "Los agentes IA pueden proponer fixes pero la review humana se queda. La convergencia automática suena bien hasta que un agente deshabilita el test que te estaba diciendo que el sistema está roto. Mi loop es asistido por diseño."

## Related

- [[K8s-Deployment-Tests]] — suite de tests de despliegue que este toolkit ayuda a diagnosticar.
- [[Correlation-ID-Propagation]] — trazabilidad end-to-end usada en `./nx debug trace`.
- [[Risk-Platform-Overview]]
