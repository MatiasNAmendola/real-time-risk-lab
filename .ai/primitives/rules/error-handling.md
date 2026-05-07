---
name: error-handling
applies_to: ["**/*.java"]
priority: high
---

# Regla: error-handling

## Principio: no swallow errors

Nunca atrapar una excepcion sin hacer algo con ella:

```java
// PROHIBIDO:
try {
    doSomething();
} catch (Exception e) {
    // nada
}

// PROHIBIDO:
} catch (Exception e) {
    log.error("error"); // sin la excepcion
}

// CORRECTO:
} catch (Exception e) {
    log.error("Failed to do X correlationId={}", correlationId, e);
    throw new ServiceException("X failed", e);
}
```

## Jerarquia de excepciones

```java
// Excepciones de dominio (no exponen detalles de infraestructura):
public class BusinessException extends RuntimeException { ... }
public class ValidationException extends BusinessException { ... }
public class ConflictException extends BusinessException { ... }

// Excepciones tecnicas (para wrapear infraestructura):
public class RepositoryException extends RuntimeException { ... }
public class ExternalServiceException extends RuntimeException { ... }
```

## Mapeo a HTTP status

| Excepcion | Status HTTP |
|---|---|
| `ValidationException` | 422 Unprocessable Entity |
| `ConflictException` (idempotencia) | 409 Conflict |
| `NotFoundException` | 404 Not Found |
| `BusinessException` generica | 400 Bad Request |
| `ExternalServiceException` | 503 Service Unavailable |
| Excepcion no mapeada | 500 Internal Server Error |

## En Vert.x (Futures)

```java
// Propagar correctamente:
return repository.findById(id)
    .compose(entity -> {
        if (entity == null) return Future.failedFuture(new NotFoundException("id=" + id));
        return Future.succeededFuture(mapper.toResponse(entity));
    })
    .onFailure(err -> log.error("findById failed id={} correlationId={}", id, correlationId, err));

// Handler de errores centralizado en el Router:
router.errorHandler(500, ctx -> {
    log.error("Unhandled error", ctx.failure());
    ctx.response().setStatusCode(500)
        .end(errorJson("Internal error", ctx.get("correlationId")));
});
```

## Response body de error

```json
{
  "error": "descripcion legible del error",
  "code": "ERROR_CODE_ENUM",
  "correlationId": "uuid-para-trazar-en-logs"
}
```

## No permitido

- `e.printStackTrace()` (usar logger).
- `catch (Exception e) {}` vacio.
- Retornar `null` para indicar error (usar `Optional` o `Future.failedFuture`).
- Exponer stack traces en respuestas HTTP de produccion.
