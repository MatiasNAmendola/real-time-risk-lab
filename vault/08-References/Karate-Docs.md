---
title: Karate DSL Documentation
tags: [reference, testing, karate, atdd]
created: 2026-05-07
---

# Karate Docs

Reference for Karate DSL used in [[atdd-karate]].

## Key URLs

- https://github.com/karatelabs/karate — main repo
- https://github.com/karatelabs/karate#getting-started — quickstart
- https://github.com/karatelabs/karate/tree/master/karate-netty — mock server

## Key Features Used

- Built-in HTTP: `Given url`, `When method POST`, `Then status 200`
- JSON assertion: `match response == { score: '#number', decision: '#string' }`
- WebSocket: `karate.webSocket(url)` — async read/write
- Async + retry: `karate.waitFor(condition)` — polls until true (used for Kafka checks)
- JaCoCo agent: attach to running SUT for cross-module coverage

## Backlinks

[[atdd-karate]] · [[0006-atdd-karate-cucumber]] · [[ATDD]]
