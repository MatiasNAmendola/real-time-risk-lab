---
title: Discovery Questions — Evaluación de plataforma
tags: [methodology, discovery]
created: 2026-05-07
source: docs/02-platform-discovery-questions.md
---

# Discovery Questions

Preguntas para hacer al evaluar una nueva plataforma o sistema. Elegir 3 según contexto. Buenas preguntas de discovery demuestran que estudiaste el espacio del problema — son tan importantes como la forma en que respondés preguntas de diseño.

## Equipo y operaciones

1. "¿Cómo es la rotación on-call para el equipo de Transactional Risk, y cómo manejan incidentes a 150 TPS?"
2. "¿Cómo balancea hoy el equipo la velocidad de features con la confiabilidad cuando el error budget se está achicando?"

## Arquitectura

3. "¿Cuál es el cuello de botella actual en la arquitectura Lambda que está empujando la migración a EKS — son cold starts, límites de concurrencia, u otra cosa?"
4. "¿Cómo se versionan hoy las decisiones de riesgo? ¿Hay un schema registry de eventos en producción o la evolución de schemas se maneja por convención?"

## ML y riesgo

5. "¿Cuál es el ciclo de refresh del modelo ML online — es continuous learning o retraining periódico?"

## Crecimiento y scope

6. "¿Cómo es el camino de crecimiento de carrera para un staff engineer en este equipo — apunta más a profundidad técnica, liderazgo de equipo o influencia en producto?"

## Backlinks

[[Risk-Platform-Overview]] · [[Architecture-Question-Bank]] · [[Project-Pitch]]
