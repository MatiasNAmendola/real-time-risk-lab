---
title: Design Anti-Patterns
tags: [methodology, anti-patterns]
created: 2026-05-07
---

# Design Anti-Patterns

Qué NO hacer. Cada ítem tiene un reemplazo "en su lugar".

## Anti-patterns de lenguaje

- **"Usamos Spring Boot porque todo el mundo lo usa"** → En su lugar: "Elegimos Spring Boot por su contenedor de DI y su ecosistema; la capa de dominio sigue siendo framework-free."
- **"No estoy familiarizado con eso"** (callejón sin salida) → En su lugar: "No trabajé con X directamente, pero lo más cercano que hice es Y, y lo encararía así..."
- **"Depende"** (sin profundizar) → "Depende" solo es aceptable si inmediatamente enumerás las variables y das una recomendación concreta.
- **"Nunca tuvimos ese problema"** → Eso señala experiencia superficial. Reconocé el modo de falla aunque no lo hayas vivido.

## Anti-patterns de arquitectura

- No prometer que los frameworks resuelven la arquitectura. Los reviewers con experiencia saben que Spring no produce mágicamente buena arquitectura.
- No confundir [[Clean-Architecture]] con "poner las cosas en packages llamados `service` y `repository`". Mostrá que entendés la regla de dependencias.
- No saltearse el storytelling del fallback. Toda llamada ML o externa necesita un fallback — si no lo mencionás, se lee como que no operaste sistemas reales.

## Anti-patterns de resiliencia

- No decir "simplemente reintentamos" sin mencionar [[Idempotency]]. Retry sin idempotencia es corrupción de datos.
- No tratar al [[Circuit-Breaker]] como opcional. A 150 TPS, una llamada downstream colgada agota tu thread pool en segundos.

## Anti-patterns de Kafka

- No confundir semántica "at-least-once" con "exactly-once". Sabé la diferencia y cuándo importa cada una.
- No omitir la [[DLQ]] al hablar de fallas de consumer. "Simplemente arreglamos el bug" no es una respuesta.

## Anti-patterns de mentoring

- No presentarte como el héroe solitario que reescribió todo. El trabajo a nivel staff requiere historias en "nosotros".
- No saltearse la parte de "qué aprendió el equipo". Los reviewers senior se fijan en el multiplicador de impacto, no en el output individual.

## Backlinks

[[Architecture-Question-Bank]] · [[Architectural-Anchors]] · [[Project-Pitch]]
