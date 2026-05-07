---
name: add-comm-pattern
description: Agregar un patron de comunicacion completo (REST/SSE/WS/Webhook/Kafka) end-to-end
steps: [choose-pattern, spec, implement, atdd, observe, document]
---

# Workflow: add-comm-pattern

## Cuando usar

Cuando surge el requerimiento "podrias agregar X patron de comunicacion?" o cuando el roadmap indica integrar un nuevo canal.

## 1. Elegir el skill correspondiente

| Patron | Skill |
|---|---|
| REST endpoint | `add-rest-endpoint` |
| SSE streaming | `add-sse-stream` |
| WebSocket | `add-websocket-channel` |
| Webhook | `add-webhook-subscription` |
| Kafka producer | `add-kafka-publisher` |
| Kafka consumer | `add-kafka-consumer` |

## 2. Actualizar la especificacion

- **REST**: actualizar `openapi.yaml` antes de implementar.
- **SSE/WS/Webhook/Kafka**: actualizar `asyncapi.json` antes de implementar.

## 3. Implementar siguiendo el skill

Leer el skill correspondiente completo antes de escribir una linea de codigo.

## 4. ATDD

Seguir workflow `new-feature-atdd`:
- Feature file primero.
- RED → implementar → GREEN.

## 5. Observabilidad

Agregar span y metrica custom (skills `add-otel-custom-span`, `add-otel-custom-metric`).

## 6. Demo en cli/risk-smoke

Verificar que el smoke TUI corre el check del patron nuevo:
```bash
cd cli/risk-smoke && go run .
# El check correspondiente (rest/sse/ws/webhook/kafka/otel) debe estar verde
```

## 7. Documentar

- `update-poc-readme`: agregar el patron a la seccion "Que demuestra" del README.
- `.ai/context/exploration-state.md`: marcar como completado.
- Commit: `feat(<patron>): add <patron> communication pattern to <poc-name>`.

## Checklist end-to-end

- [ ] OpenAPI o AsyncAPI actualizada
- [ ] Skill correspondiente seguido
- [ ] ATDD feature file escrito antes de implementar
- [ ] Tests verdes
- [ ] OTEL span y metrica
- [ ] Smoke TUI pasa el check
- [ ] README del PoC actualizado
- [ ] exploration-state.md actualizado
