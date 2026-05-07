# 11 — ATDD: Acceptance Test Driven Development

## Qué es ATDD y en qué se diferencia de TDD

| Aspecto | TDD | ATDD |
|---|---|---|
| Quién escribe los tests | Desarrollador | Producto + QA + Dev (los tres juntos) |
| Cuándo | Antes del código de implementación | Antes de empezar la historia |
| Lenguaje | Código (JUnit, Mockito) | Lenguaje natural (Gherkin: Given/When/Then) |
| Foco | Unidad técnica | Comportamiento de negocio |
| Coverage que importa | Líneas de código | Escenarios de aceptación |
| Falla típica | Test rojo → escribir código → verde | Feature roja → entender requerimiento → implementar → verde |
| Audiencia del test | Otros devs | Stakeholders no técnicos también |

ATDD no reemplaza TDD: lo envuelve. La feature en Gherkin describe el QUÉ; los tests unitarios TDD describen el CÓMO. Para el rol de staff/architect que evalúa procesos, ATDD demuestra madurez de práctica más allá de "sé escribir tests".

## El ciclo ATDD

```
Producto explica el problema
        ↓
Conversación 3-amigos (Producto + QA + Dev)
        ↓
Feature en Gherkin escrita en conjunto
        ↓
Feature roja (no hay implementación)
        ↓
Dev hace TDD para los pedazos internos
        ↓
Feature verde
        ↓
Demo a Producto
```

Si Producto acepta la feature verde, la historia está terminada. Si no, se ajusta el Gherkin (no el código primero) y el ciclo se repite.

## Key Design Principle

> "TDD me protege contra regresiones técnicas; ATDD me protege contra construir lo que no era. Las dos cosas no compiten, se complementan."

> "El feature file en Gherkin es el contrato con el negocio. Si Producto no puede leerlo, no es ATDD, es una suite de integration tests con sintaxis bonita."

## Por qué ATDD en un sistema de fraude

Para Transactional Risk, ATDD es especialmente valioso porque:

1. **Las decisiones de fraude son auditables**. Una feature como "el sistema declina toda transacción mayor a 200000 si el cliente tiene menos de 30 días" es un contrato regulatorio, no una preferencia técnica.
2. **El riesgo regulatorio no admite ambigüedad**. Si Compliance puede leer y firmar el feature file, el contrato queda explícito antes del código.
3. **Cambia más el negocio que la tecnología**. Las reglas de fraude cambian semanalmente; los adapters cambian rara vez. ATDD ataca el eje de cambio real.
4. **Los modelos ML necesitan policy testing**. Una feature como "si el modelo no responde en 80ms, el sistema cae a reglas determinísticas" es exactamente lo que un staff engineer querría que esté verificado en CI.

## Cómo organizamos las pruebas en estos PoC

Dos suites paralelas, una por PoC:

### `poc/java-vertx-distributed/atdd-tests/` — Karate
- Para la PoC distribuida con todos los patrones de comunicación (REST, SSE, WS, Webhook, Kafka).
- Karate es ideal porque cada feature levanta llamadas HTTP/WS reales contra los servicios corriendo en docker compose.
- Los features cubren end-to-end, no piezas internas.

### `tests/risk-engine-atdd/` — Cucumber-JVM
- Para la PoC bare-javac.
- Módulo Maven independiente que NO contamina la build javac directa de la PoC.
- Cucumber porque permite step definitions Java contra el use case directamente, sin necesidad de levantar HTTP.

Ambos generan reports JaCoCo. La métrica que miramos:

- Coverage de líneas: > 75% en `domain.service`, `application.usecase`, `infrastructure.resilience`.
- Coverage de escenarios: cada use case y cada adapter aparece en al menos 1 feature.

## Coverage de escenarios — la matriz importa más que el porcentaje

```
Feature                    │ HTTP │ EventBus │ DB │ Kafka │ ML │ Outbox │ Idempot │ Fallback
───────────────────────────┼──────┼──────────┼────┼───────┼────┼────────┼─────────┼─────────
Approve low risk           │  ●   │    ●     │ ●  │       │    │        │         │
High amount declined       │  ●   │    ●     │ ●  │   ●   │ ●  │   ●    │         │
Webhook callback on review │  ●   │    ●     │ ●  │   ●   │ ●  │   ●    │         │
Idempotency on retry       │  ●   │    ●     │    │       │    │        │    ●    │
ML timeout fallback        │  ●   │    ●     │ ●  │       │ ●  │   ●    │         │   ●
End-to-end OTEL trace      │  ●   │    ●     │ ●  │   ●   │ ●  │   ●    │         │
```

Una matriz como esta this is the kind of evidence that communicates real coverage: "no es que tengo 80% de coverage, es que cada componente está cubierto por al menos un feature de negocio".

## Anti-patrones comunes (qué NO hacer)

- **Gherkin como pseudocódigo**. "Given el método getUser devuelve null" es TDD disfrazado de ATDD. La feature debe leer como reglas de negocio.
- **Steps que tocan implementación**. Si el step necesita mockear un repositorio interno, probablemente sea un test unitario, no acceptance.
- **Una feature por endpoint**. Las features son por comportamiento de negocio, no por route. Una feature puede tocar 3 endpoints; un endpoint puede aparecer en 5 features.
- **Tests que solo passan en local**. Si requieren manipulación manual de DB o tiempo, son frágiles. Usar fixtures e idempotencia.
- **Coverage como meta principal**. 95% de líneas con escenarios pobres es peor que 70% con la matriz cubierta.
- **Skipear @wip por mucho tiempo**. Un `@wip` que vive más de un sprint es deuda. Borrar o priorizar.

## Demo Flow

Para mostrar ATDD vivo, tres pasos:

1. **Abrir un feature file** (`05_webhook_callback.feature`) y leer en voz alta el escenario. "Esto lo entiende Producto, no necesito traducirle."
2. **Correr `mvn test`** y mostrar el feature pasando con `--reports`. Karate genera HTML con el flujo HTTP visible.
3. **Tocar la regla de negocio** en el código (cambiar el threshold de DECLINE de 200000 a 100000) y volver a correr — el feature `02_rest_decision.feature` que esperaba REVIEW para 150000 ahora falla. Mostrar el output con la línea exacta que rompió.

> "El feature falla en el lenguaje de Producto, no en el lenguaje del compilador. Esa diferencia es ATDD."

## Cuándo NO conviene ATDD

ATDD agrega ceremonia. No conviene para:

- Spike técnicos / proof-of-concept: el contrato no está estable, el feature file caduca rápido.
- Bugs de bajo nivel (off-by-one, NPE en un mapper): test unitario directo, no Gherkin.
- Equipos donde Producto no participa: si los features los escribe el dev solo, son integration tests con sintaxis BDD. Igual valen pero el ROI es menor.
- Sistemas donde el comportamiento cambia más rápido de lo que se puede escribir el Gherkin (rara vez en fraude — ahí ATDD brilla).

## Stack que elegimos

| PoC | Framework | Por qué |
|---|---|---|
| Vert.x distribuida | Karate | E2E HTTP/WS/Kafka, sintaxis terse, sin step definitions Java triviales |
| risk-engine bare-javac | Cucumber-JVM 7 | Acceso programático directo al use case, sin servidor HTTP de por medio |
| Coverage | JaCoCo 0.8 | Estándar JVM, integra con Maven y CI |

Para Go (el smoke runner), no agregamos ATDD propio — los smoke checks ya son acceptance tests implícitos. Si fuera necesario formalizarlo, `cucumber/godog` es la elección.

## Cierre

ATDD no es una herramienta, es un acuerdo de equipo. Los frameworks (Karate, Cucumber, Concordion) son cosméticos. Lo que cambia el juego es que Producto pueda leer el contrato antes de que el primer commit exista. In a design discussion, if asked "¿cómo asegurás que lo que construís es lo que pidió el negocio?", esta es la respuesta — no "tengo 90% de coverage".
