---
title: Virtual Threads (Project Loom)
tags: [concept, java, concurrency, performance]
created: 2026-05-07
---

# Virtual Threads (Project Loom)

Threads livianos manejados por la JVM, introducidos en Java 21 (preview) y estables desde Java 21+. Cada virtual thread se respalda en un número chico de threads (carriers) del SO. Permite escribir código bloqueante (JDBC, HTTP) a escala thread-per-request — millones de virtual threads concurrentes con bajo overhead de memoria.

## Cuándo usar

Workloads de I/O bloqueante donde la programación reactiva (Vert.x, WebFlux) es overkill o aumenta la complejidad. Java 21+ hace de los virtual threads el default para `Executors.newVirtualThreadPerTaskExecutor()`.

## Cuándo NO usar

Tareas CPU-bound — los virtual threads no ayudan ahí; usar platform threads con pools acotados.

## En este proyecto

El servidor HTTP de [[java-risk-engine]] usa `Executors.newVirtualThreadPerTaskExecutor()`. Cada request recibe un virtual thread — sin ceremonia reactiva. Ver [[0001-java-25-lts]].

## Principio de diseño

"Los virtual threads te dan la escalabilidad de lo reactivo sin el callback hell. El código bloqueante se lee como secuencial y escala como async."

## Backlinks

[[0001-java-25-lts]] · [[java-risk-engine]] · [[Latency-Budget]]
