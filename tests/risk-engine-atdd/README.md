# Real-Time Risk Lab — ATDD Module (Cucumber-JVM)

Acceptance-test suite for the bare-javac risk engine PoC.
Standalone Gradle project — does not modify any file under `poc/no-vertx-clean-engine/`.

---

## Qué es ATDD y por qué Cucumber

**ATDD (Acceptance Test-Driven Development)** es la práctica de escribir criterios de aceptación como tests ejecutables *antes* de implementar la funcionalidad. Los tests viven en lenguaje de negocio (Gherkin), no en código técnico, y actúan como contrato entre producto, QA e ingeniería.

**Cucumber-JVM** fue elegido porque:
- Los feature files son legibles por stakeholders no técnicos.
- El motor Cucumber-JVM `cucumber-junit-platform-engine` integra nativamente con JUnit 5.
- PicoContainer provee DI ligera sin Spring, coherente con el mandato "Clean Architecture sin frameworks" de la PoC.
- Los tags `@wip` / `@ignore` permiten documentar limitaciones del PoC sin romper el build.

---

## Reports

Después de cada `./gradlew test` (o `./scripts/atdd-bare.sh`), el script `scripts/report.sh` genera un árbol de reportes en:

```
<repo-root>/out/atdd-cucumber/
├── latest -> 2026-05-07T17-00-00/   # symlink al último run
└── 2026-05-07T17-00-00/
    ├── summary.md                   # tabla resumen con links a cada feature
    ├── summary.txt                  # version plain-text (sin markdown)
    ├── features/
    │   ├── 01-approve-low-risk-transactions.md
    │   ├── 02-decline-or-review-high-amount-transactio.md
    │   └── ...
    ├── coverage/
    │   ├── jacoco-summary.md        # cobertura por paquete
    │   └── matrix.md                # qué feature cubre qué capa
    ├── full.log                     # log completo (copia de build/cucumber.log)
    └── meta.json                    # timestamp, duration, exit code, env
```

Comandos útiles:

```bash
# Ver resumen del último run
cat out/atdd-cucumber/latest/summary.md

# Ver detalle de un feature específico
cat out/atdd-cucumber/latest/features/01-approve-low-risk-transactions.md

# Ver cobertura por paquete
cat out/atdd-cucumber/latest/coverage/jacoco-summary.md

# Ver matrix feature vs. capa
cat out/atdd-cucumber/latest/coverage/matrix.md
```

El symlink `latest` siempre apunta al run más reciente. Los runs anteriores se conservan en su propio directorio con timestamp.

---

## Cómo correr

```bash
# Todos los features (excluye @wip por default)
cd tests/risk-engine-atdd
./gradlew test

# Desde la raíz del repo
./scripts/atdd-bare.sh
```

## Correr un feature solo por tag

```bash
./gradlew test -Dcucumber.filter.tags=@idempotency
./gradlew test -Dcucumber.filter.tags=@outbox
./gradlew test -Dcucumber.filter.tags=@correlation-id
./gradlew test -Dcucumber.filter.tags=@new-device
./gradlew test -Dcucumber.filter.tags=@high-amount
./gradlew test -Dcucumber.filter.tags=@low-risk
```

## Correr incluyendo @wip

```bash
./gradlew test -Dcucumber.filter.tags="@wip"
```

## Coverage

```bash
./gradlew test jacocoTestReport
# Reporte en: build/reports/jacoco/test/index.html

# O con script:
./scripts/atdd-bare-coverage.sh
```

---

## Coverage matrix por feature

| Feature file                              | Tag              | Scenarios | Status  | Packages cubiertos                                        |
|-------------------------------------------|------------------|-----------|---------|-----------------------------------------------------------|
| 01_evaluate_low_risk                      | @low-risk        | 1 activo  | PASS    | domain.service, application.usecase, infrastructure.ml    |
| 01_evaluate_low_risk (ML bypass)          | @wip             | 1 wip     | SKIP    | —  (PoC no expone seam para fast-path pre-ML)             |
| 02_evaluate_high_amount                   | @high-amount     | 2         | PASS    | domain.rule, domain.service, application.usecase          |
| 03_evaluate_new_device_young_customer     | @new-device      | 2         | PASS    | domain.rule, domain.service, application.usecase          |
| 04_idempotency                            | @idempotency     | 1         | PASS    | application.usecase, infra.repository.idempotency         |
| 05_outbox_pattern                         | @outbox          | 2         | PASS    | infra.repository.event, domain.repository                 |
| 06_ml_fallback                            | @ml-fallback @wip | 1 wip    | SKIP    | —  (FakeRiskModelScorer no tiene seam para forzar timeout)|
| 07_correlation_id_propagation             | @correlation-id  | 2         | PASS    | domain.entity, application.usecase, infra.repository.event|

**Total:** 10 scenarios activos PASS / 2 @wip SKIP

---

## Cómo agregar un feature nuevo

1. **Escribir el Gherkin primero** — crea un archivo en `src/test/resources/features/NN_nombre.feature`.
2. **Correr `./gradlew test`** — Cucumber reporta los steps como "undefined" y genera stubs.
3. **Ver fallar** — el step sin implementar aparece como `PENDING`.
4. **Implementar los steps** — agrega un archivo en `src/test/java/io/riskplatform/atdd/steps/`.
5. **Volver a correr** — los scenarios deben pasar.

```
feature file → FAIL (pending) → step impl → PASS
```

---

## Limitaciones documentadas del PoC

### Feature 06: ML timeout fallback (@wip)
`FakeRiskModelScorer` usa `RandomGenerator` interno y no expone constructor para inyectar latencia fija.
`RiskApplicationFactory` hardcodea la instanciación y tampoco expone un `RiskApplicationFactory(RiskModelScorer scorer)`.
Para habilitar el test sin modificar el PoC, el PoC debería exponer uno de:
- `RiskApplicationFactory(ClockPort, RiskModelScorer)` — constructor adicional.
- `RiskApplicationFactory.Builder` — builder pattern.
- `FakeRiskModelScorer(long fixedLatencyMs)` — constructor con latencia configurable.

### Feature 01: ML bypass para monto bajo (@wip)
La PoC no tiene una `LowAmountFastApproveRule` ni fast-path pre-ML. Para monto bajo, el motor siempre llama al modelo.
Para implementar el test, el PoC debería agregar una regla de aprobación inmediata para montos < umbral.

---

## ATDD vs TDD

| Aspecto          | TDD                                     | ATDD                                         |
|------------------|-----------------------------------------|----------------------------------------------|
| Lenguaje         | Código Java (JUnit assertions)          | Gherkin (Given/When/Then)                    |
| Audiencia        | Desarrolladores                         | Dev + QA + Producto                          |
| Nivel de test    | Unitario / de componente                | Aceptación / integración                     |
| Ciclo            | Red → Green → Refactor                  | Gherkin → Fail → Steps → Pass                |
| Documenta        | Contrato de método/clase                | Comportamiento de negocio                    |
| Herramienta aquí | (el PoC bare-javac no tiene)            | Cucumber-JVM + JUnit 5 + AssertJ             |

---

## Stack técnico

- **Cucumber-JVM 7.x** con `cucumber-junit-platform-engine` (JUnit 5 nativo — NO JUnit 4).
- **JUnit 5** (`junit-platform-suite`, `junit-jupiter`).
- **PicoContainer** para DI en step definitions via `World`.
- **AssertJ** para assertions fluidas.
- **JaCoCo 0.8.x** para cobertura de las clases de la PoC compiladas localmente.
- **Java 21** (fuentes del PoC + tests).
- **Opción A**: `build-helper-gradle-plugin add-source` compila las fuentes del PoC directamente.
  No hay dependencia de clases precompiladas; el PoC permanece sin modificaciones.
