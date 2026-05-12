---
adr: "0047"
title: Política de versión de Go — Go 1.26.2 con goenv
status: accepted
date: 2026-05-07
tags: [decision/accepted, adr, go, versioning, toolchain, goenv]
source_archive: docs/23-go-version-policy.md (migrado 2026-05-12)
---

# ADR-0047: Política de versión de Go — Go 1.26.2 con goenv

## Contexto

El proyecto incluye un smoke runner CLI escrito en Go (`cli/risk-smoke/`). Se necesita definir una versión objetivo estable y un mecanismo de gestión por proyecto para evitar inconsistencias entre entornos de desarrollo y CI.

## Decisión

**Versión objetivo**: Go 1.26.2 (verificado 2026-05-07).

**Mecanismo de gestión**: `goenv` con `.go-version` commiteado al repo.

`cli/risk-smoke/go.mod` declara `go 1.26`.

## Por qué esta versión

- **Estado equivalente a LTS estable**: Go sigue una cadencia de release semestral (febrero y agosto). Go 1.26 salió en febrero de 2026 y recibirá patches de seguridad hasta que salga Go 1.28 (agosto 2027).
- **Mejoras de toolchain y performance**: Go 1.26 continúa los refinamientos en PGO (profile-guided optimization), el iterador range-over-function (estabilizado en 1.22+) y latencia mejorada del garbage collector — todo relevante para un servicio de 150 TPS, p99 < 300ms.
- **Compatibilidad con dependencias del proyecto**: charmbracelet/bubbletea 1.3.x, charmbracelet/lipgloss 1.1.x, twmb/franz-go 1.21.x y coder/websocket 1.8.x declaran `go 1.21` o menor como mínimo, así que compilan limpio en 1.26.
- **Sin breaking changes**: aplica la garantía de compatibilidad de Go 1. Todo código que compilaba en 1.21 compila igual en 1.26.

## Consecuencias

### Cómo instalar

#### Recomendado: goenv

```bash
brew install goenv
goenv install 1.26.2
goenv global 1.26.2
```

Para configurar por proyecto:

```bash
echo "1.26.2" > .go-version
goenv local 1.26.2
```

#### Vía setup del proyecto

```bash
./setup.sh --only languages
```

### Por qué goenv sobre brew

`brew install go` reemplaza el binario global de Go. Si hay servicios en distintas versiones, brew hace difícil cambiar. goenv permite:

- Versión por proyecto vía archivo `.go-version` (commiteado al repo).
- Swap a nivel shell: `goenv global 1.26.2` sin tocar otros proyectos.
- Misma ergonomía que `jenv` (Java), `nvm` (Node) o `sdkman` (ecosistema JVM).

## Referencia de cadencia de releases

| Versión | Release | Fin de soporte de seguridad |
|---------|---------|------------------------|
| 1.25    | Ago 2025 | Feb 2027 |
| 1.26    | Feb 2026 | Ago 2027 |
| 1.27    | Ago 2026 (esperado) | Feb 2028 |

Fuente: https://go.dev/doc/devel/release

## Alternativas consideradas

- `brew install go` directo — descartado porque hace difícil mantener múltiples versiones de Go.
- Pinear a Go 1.21 (mínimo de las deps) — descartado porque se pierden mejoras de PGO y GC.

## Principio de diseño clave

> "Go cambia rapido. Tener un version manager por proyecto evita el caos de devs con versiones distintas. Es el mismo argumento que para Java con jenv o sdkman. Cada repo declara su version en `.go-version`; el CI lo respeta; no hay sorpresas."

## Relacionado

- [[0035-java-go-polyglot]] — decisión de usar Go para el CLI de smoke.
- [[Java-vs-Go-Performance]] — comparación de performance entre Java y Go.
- [[Risk-Platform-Overview]]
