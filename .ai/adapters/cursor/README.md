# Adapter: Cursor

Cursor lee rules MDC (`.cursor/rules/*.mdc`) con frontmatter especifico.

## Archivos que usa este adapter

| Archivo | Proposito |
|---|---|
| `.cursor/rules/00-project.mdc` | Contexto del proyecto, siempre activo |
| `.cursor/rules/10-architecture.mdc` | Reglas de arquitectura, activo en .java |
| `.cursor/rules/20-testing.mdc` | Reglas de testing, activo en .java y .feature |

## Como Cursor consume las primitivas

1. Cursor lee automaticamente los archivos `.mdc` en `.cursor/rules/`.
2. Los archivos con `alwaysApply: true` se aplican en todas las conversaciones.
3. Los archivos con `globs` se aplican solo cuando esos archivos estan en contexto.
4. Skills se acceden referenciando el archivo: `@.ai/primitives/skills/add-rest-endpoint.md`.

## Frontmatter MDC

```yaml
---
description: Descripcion corta de la rule
globs: ["**/*.java", "**/pom.xml"]
alwaysApply: false
---
```

## Limitaciones conocidas

- Los archivos MDC no soportan imports nativos. El contenido debe ser autocontenido o referenciar otros archivos con `@`.
- Los globs en Cursor MDC son relativos a la raiz del repo.
- `alwaysApply: true` aumenta el uso de tokens — solo para las rules mas criticas.

## Instalar

```bash
./.ai/adapters/cursor/install.sh
```

## Formato MDC (desde Cursor v2.2)

El formato `.mdc` (Markdown con frontmatter YAML) es el canonico desde v2.2.
El legacy `.cursorrules` (archivo unico en root) sigue funcionando pero NO debe usarse en nuevos proyectos.
Este adapter genera SOLO archivos `.mdc` — no genera `.cursorrules`.

## Documentacion oficial

- https://docs.cursor.com/context/rules-for-ai
- https://forum.cursor.com/t/optimal-structure-for-mdc-rules-files/52260
