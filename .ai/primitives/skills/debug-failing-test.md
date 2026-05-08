---
name: debug-failing-test
intent: Diagnosticar y resolver un test ATDD o unitario que falla en el CI o localmente
inputs: [test_name, error_message, module_path]
preconditions:
  - ./gradlew test falla con mensaje de error identificable
postconditions:
  - Causa raiz identificada
  - Test verde
  - Fix documentado en commit message
related_rules: [testing-atdd, java-version, error-handling, observability-otel]
---

# Skill: debug-failing-test

## Pasos por tipo de error

### ClassNotFoundException / NoClassDefFoundError
- Verificar dependencias en build.gradle.kts del modulo.
- `./gradlew dependency:tree -pl <module>` para ver el classpath.
- Verificar que la clase no fue movida de paquete (refactor sin actualizar imports).

### Test de integracion falla con "Connection refused"
- Verificar que el servidor esta arrancado: `lsof -i :<port>`.
- En tests ATDD: el `@BeforeAll` debe arrancar el verticle y esperar a que este listo (healthcheck).
- Timeout del servidor: aumentar espera en `@BeforeAll` si la JVM es lenta.

### Karate: "expected 200, got 404"
- Verificar que la ruta esta registrada exactamente igual (case-sensitive).
- `curl -v http://localhost:<port><path>` para confirmar fuera del test.
- Revisar si hay prefijo de ruta en el Router (ej. `/api/v1/`).

### Cucumber: "Undefined step"
- El step definition no matchea exactamente el texto del feature.
- Verificar el glue path en el runner (`@ConfigurationParameter(key = GLUE_PROPERTY_NAME, ...)`).
- Cucumber 7+ distingue entre `{string}` y `{word}` en parametros.

### "NullPointerException" en test unitario
- Revisar setup: `@BeforeEach` no inicializa el objeto.
- Verificar que los mocks estan configurados antes del metodo bajo test.

### Test flaky (falla intermitentemente)
- Causa comun: race condition en tests asincronos.
- En Vert.x: usar `VertxTestContext` con `checkpoint()` en lugar de `Thread.sleep`.
- Incrementar timeout del test, no el sleep.

## Herramientas de diagnostico

```bash
# Logs detallados de JUnit/Gradle test
./gradlew test -pl <module> -Dsurefire.useFile=false -Dtest=<TestClass>#<method>

# Ver stacktrace completo
./gradlew test -pl <module> -e -X 2>&1 | grep -A 20 "FAILED"
```

## Notas
- Nunca silenciar un test fallando con `@Disabled` sin crear un issue.
- Si el test falla en CI pero no localmente: revisar variables de entorno, puertos, Docker disponible.
