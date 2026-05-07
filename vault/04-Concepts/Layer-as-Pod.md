---
title: Layer-as-Pod Pattern
tags: [concept, pattern/structural, kubernetes, architecture]
created: 2026-05-07
---

# Layer-as-Pod

Nuestro patrón para el PoC Vert.x: cada capa arquitectural (controller, usecase, repository) corre como Pod / contenedor Docker independiente. Las capas se comunican vía el event bus clustered de Vert.x (Hazelcast TCP). Los límites físicos del deployment coinciden con los límites lógicos de la arquitectura.

## Cuándo usar

Para demostrar escalabilidad independiente por capa. Si el cuello de botella es la CPU del motor de riesgo, escalá los pods `usecase-app` sin tocar `controller-app`. También útil para migración escalonada: reemplazar un pod a la vez.

## Cuándo NO usar

Servicios de bajo tráfico donde el overhead operacional de 3+ deployments separados supera al beneficio.

## En este proyecto

[[java-vertx-distributed]]: `controller-app`, `usecase-app`, `repository-app` son 3 imágenes Docker separadas. El Helm chart de [[k8s-local]] los despliega como 3 Deployments separados. La segmentación de red espeja el diseño de namespaces del EKS productivo.

## Principio de diseño

"Layer-as-Pod no es solo un diagrama de arquitectura — es una herramienta operacional. Escalás la capa que está bajo presión, no la aplicación entera."

## Backlinks

[[Clean-Architecture]] · [[Hexagonal-Architecture]] · [[java-vertx-distributed]] · [[k8s-local]] · [[0003-vertx-for-distributed-poc]]
