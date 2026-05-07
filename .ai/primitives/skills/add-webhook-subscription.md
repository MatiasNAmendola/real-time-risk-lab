---
name: add-webhook-subscription
intent: Agregar registro y entrega de webhooks para notificar decisiones de riesgo a sistemas externos
inputs: [event_type, payload_schema, retry_policy]
preconditions:
  - REST endpoint base funcionando
  - Postgres disponible para persistir suscripciones
postconditions:
  - POST /webhooks/subscriptions registra URL + evento
  - Al ocurrir el evento, se hace POST HTTP al callback con firma HMAC-SHA256
  - Reintentos con backoff exponencial (3 intentos)
  - ATDD: registrar, disparar evento, recibir callback en listener local
related_rules: [java-version, events-versioning, communication-patterns, error-handling, testing-atdd]
---

# Skill: add-webhook-subscription

## Pasos

1. **Entidad de dominio** `domain/entity/WebhookSubscription.java`:
   - Fields: `id`, `callbackUrl`, `eventType`, `secret`, `active`, `createdAt`.

2. **Puerto de salida** `domain/repository/WebhookSubscriptionRepository.java`:
   - `save(WebhookSubscription)`, `findByEventType(String)`.

3. **Use case** `application/usecase/webhook/RegisterWebhookUseCase.java`:
   - Valida URL (http/https), genera `secret` aleatorio.
   - Persiste via repository.

4. **Endpoint de registro**:
   - `POST /webhooks/subscriptions` -> 201 con `{ "id": "...", "secret": "..." }`.
   - `DELETE /webhooks/subscriptions/{id}` -> 204.

5. **Deliverer** `infrastructure/webhook/WebhookDeliverer.java`:
   - Firma payload con HMAC-SHA256 usando el secret.
   - Header: `X-Webhook-Signature: sha256=<hex>`.
   - Reintentos con backoff: 1s, 5s, 30s. Despues de 3 fallos, marcar como failed.
   - Usar `vertx.createHttpClient()` para llamadas salientes no bloqueantes.

6. **Integrar en el flujo de decision**:
   - Al completar una decision, publicar evento en EventBus.
   - `WebhookDeliverer` consume el evento y despacha a todos los subscribers del tipo.

7. **ATDD**:
   ```gherkin
   Scenario: webhook entrega decision al subscriber
     Given a webhook is registered for "RISK_DECISION" with callback "http://localhost:9999/hook"
     And a local HTTP listener is running on port 9999
     When I POST a transaction that results in DECLINE
     Then the listener receives a POST within 5 seconds
     And the payload contains "decision": "DECLINE"
     And the signature header is valid
   ```

## Notas
- Nunca loguear el `secret` completo. Solo los primeros 4 caracteres para debugging.
- El secret debe almacenarse encriptado en Postgres (o via External Secrets).
