# 32 - Toolkit de fallas y debug

## 1. Por qué este bundle

Los sistemas distribuidos fallan en capas: una sola falla de test puede tener su root
cause en una mala configuración de red Docker, un memory limit demasiado bajo o una
regresión genuina en lógica de negocio. Sin tooling estructurado, diagnosticar qué capa
es responsable consume más tiempo que el fix mismo.

Este bundle provee tres herramientas complementarias:

1. **Failures aggregator** — vista unificada de fallas a través de todas las suites de tests.
2. **Auto-fix dispatcher** — clusteriza fallas por causa y genera prompts de fix
   accionables para revisión humana (nunca aplica cambios automáticamente).
3. **Comandos de debug** — investigación a nivel servicio: logs, diagnóstico heurístico,
   snapshots forenses, lookup de tracing distribuido y probes de DNS.

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
| Summary del test runner | `out/test-runner/latest/summary.md` |

### Heurísticas de root cause

El aggregator clasifica cada mensaje de falla con una heurística:

| Patrón | Root cause |
|---|---|
| `Connection refused`, `EHOSTUNREACH` | servicio caído |
| `Timeout`, `Timed out` | servicio lento |
| `NXDOMAIN`, `UnknownHostException` | problema de red/DNS |
| `AssertionError` | problema de lógica de test |
| `OutOfMemoryError` | mem_limit demasiado bajo |
| `ClassNotFoundException`, `NoClassDefFoundError` | problema de classpath |

Cada falla incluye un fix sugerido derivado de la heurística.

### CLI

```bash
./nx failures                          # escanea todas las suites
./nx failures --suite atdd-karate      # solo una suite
./nx failures --json                   # output machine-readable
./nx failures --since 1h               # solo resultados recientes
```

El output también se escribe a `out/failures/<ts>/summary.md` con un symlink `latest`.

---

## 3. Auto-fix dispatcher

**Script:** `.ai/scripts/auto-fix-dispatcher.py`

El dispatcher lee el output de fallas, clusteriza por root cause y genera un template de
prompt por cluster. El prompt puede pegarse en Claude Code o pasarse vía CLI.

**El dispatcher nunca aplica cambios.** Solo los propone.

### Safeguards (hardcoded, no negociables)

```python
FORBIDDEN_FIX_PATTERNS = [
    r"@(Disabled|Ignore|Skip|wip)",     # nunca deshabilitar tests
    r"\.skip\s*\(",                      # nunca .skip()
    r"//\s*TODO:?\s*re-enable",         # nunca deshabilitar via comment
    r"//\s*assert\s",                   # nunca comentar assertions
    r"#\s*assert\s",
    r"coverage.*=\s*[0-9]+\s*[#//]",   # nunca bajar thresholds de coverage
]

MAX_FILES_PER_FIX = 5
```

Si un prompt generado matchea cualquier patrón prohibido o referencia más de cinco
archivos, el dispatcher requiere confirmación extra antes de proceder.

### CLI

```bash
./nx test --auto-fix --dry-run         # muestra plan, sin archivos de output
./nx test --auto-fix --apply           # confirma interactivamente por cluster
python3 .ai/scripts/auto-fix-dispatcher.py --dry-run
python3 .ai/scripts/auto-fix-dispatcher.py --apply
python3 .ai/scripts/auto-fix-dispatcher.py --max-iter 3
```

### Modo `--autonomous`

```
WARNING: --autonomous mode disables human review.
This is the worst case for AI alignment. Use --apply with confirmation instead.
Are you sure? Type 'YES I UNDERSTAND THE RISK': _
```

El modo autónomo requiere confirmación explícita tipeada y debe evitarse en flujos
productivos.

---

## 4. Comandos de debug

**Script:** `.ai/scripts/debug-runner.py`

### logs

```bash
./nx logs                              # todos los servicios (modo flags)
./nx logs --service controller-app
./nx logs --since 5m
./nx logs --grep ERROR
./nx logs --errors                     # ERROR|FATAL|WARN|Exception|panic
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
5. Sanity de OpenObserve — consulta `/api/default/_search`; alerta si hay 0 traces con servicios arriba.

### snapshot

```bash
./nx debug snapshot
```

Escribe `out/debug/<ts>/`:

- `compose-ps.txt`
- `docker-stats.txt`
- `logs-<service>.log` (últimas 200 líneas por servicio)
- `host-info.txt` (uname, memoria, disco, uptime)
- `openobserve-summary.json`
- `meta.json` (ts, mode, duration)

### trace

```bash
./nx debug trace abc-123
```

Consulta OpenObserve por trace ID, imprime el timeline de spans entre servicios y emite
la URL de UI.

### probe

```bash
./nx debug probe
```

Corre `getent hosts` desde cada servicio Java a todos los demás. Reporta NXDOMAIN al
instante y sugiere fixes de configuración de red.

---

## 5. Cuándo usar cada herramienta (matriz de decisión)

| Situación | Herramienta |
|---|---|
| El pipeline de CI muestra fallas, hace falta un summary | `./nx failures` |
| Quiero sugerencias de fix asistidas por IA | `./nx test --auto-fix --dry-run` |
| Servicio no responde después de un deploy | `./nx debug diagnose` |
| Hace falta compartir un bug report con contexto | `./nx debug snapshot` |
| Investigando un request específico de usuario | `./nx debug trace <id>` |
| Llamadas inter-servicio fallando | `./nx debug probe` |
| Chequeo completo post-deploy | `./nx audit failures` |
| Tests fallan + quiero diagnóstico instantáneo | `./nx test --diagnose-on-fail` |

---

## 6. Anti-patrones

### Modo autónomo como default

Correr `--autonomous` en pipelines de CI significa que un agente puede modificar código
productivo sin ningún humano en el loop. La primera vez que "arregle" un test
deshabilitándolo en lugar de arreglar el servicio subyacente, perdés la early warning de
que el sistema está roto.

### Saltear el aggregator

Ir directo al auto-fix dispatcher sin revisar el summary de fallas significa que se
proponen fixes sin entender el scope completo. Siempre correr `./nx failures` primero.

### Tratar al "suggested fix" como autoritativo

Las heurísticas son basadas en patrones. "Connection refused" apuntando a "servicio
caído" suele tener razón, pero también podría ser una mala config de puerto en un test.
Leer el error real antes de aplicar cualquier fix.

### Modificar assertions para que los tests pasen

Es el anti-patrón más peligroso. Una assertion que falla es una señal. Cambiar el valor
esperado para matchear el valor real sin entender la regresión destruye el valor del
test. El `FORBIDDEN_FIX_PATTERNS` del dispatcher enforza esto a nivel tooling.

---

## 7. Principio de diseño clave

> "Los agentes IA pueden proponer fixes pero la review humana se queda. La convergencia
> automática suena bien hasta que un agente deshabilita el test que te estaba diciendo
> que el sistema está roto. Mi loop es asistido por diseño."

---

## 8. Referencia de archivos

| Archivo | Propósito |
|---|---|
| `.ai/scripts/failures-aggregator.py` | Parsea todos los formatos de suite, clasifica fallas |
| `.ai/scripts/auto-fix-dispatcher.py` | Clusteriza fallas, genera prompts de fix |
| `.ai/scripts/debug-runner.py` | logs, diagnose, snapshot, trace, probe |
| `.ai/scripts/test_failures_aggregator.py` | 22 tests para el aggregator |
| `.ai/scripts/test_auto_fix_dispatcher.py` | 17 tests para el dispatcher |
| `.ai/scripts/test_debug_runner.py` | 15 tests para el debug runner |
| `out/failures/latest/summary.md` | Último summary de fallas (generado) |
| `out/debug/latest/` | Último snapshot forense (generado) |
