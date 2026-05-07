# Adapter: GitHub Copilot

GitHub Copilot lee `.github/copilot-instructions.md` como archivo global de instrucciones, y desde noviembre 2025 soporta instrucciones por archivo/lenguaje en `.github/instructions/`.

## Archivos que usa este adapter

| Archivo | Proposito |
|---|---|
| `.github/copilot-instructions.md` | Instrucciones globales del repo (sin frontmatter) |
| `.github/instructions/*.instructions.md` | Instrucciones per-file/per-language con frontmatter |

## Jerarquia de instrucciones

1. Personal instructions (usuario) — mayor prioridad
2. Repository instructions (`.github/copilot-instructions.md`) — este repo
3. Organization instructions — menor prioridad

## Formato per-file (`.github/instructions/`)

```yaml
---
applyTo: "**/*.java"
# Multiples patrones:
# applyTo: "**/*.java,**/pom.xml"
# Excluir agentes especificos (desde noviembre 2025):
# excludeAgent: "code-review"
# excludeAgent: "coding-agent"
---
# Java-specific instructions
```

Campos:
- `applyTo` (glob): archivos a los que aplica
- `excludeAgent`: `"code-review"` o `"coding-agent"` para excluir agentes especificos

## Importante

`.github/copilot-instructions.md` NO lleva frontmatter — todo el contenido es instruccion plana.
Los archivos `.instructions.md` en `.github/instructions/` SI llevan frontmatter con `applyTo`.

## Como Copilot consume las primitivas

1. Copilot lee `.github/copilot-instructions.md` al abrir el repo.
2. El usuario puede referenciar archivos adicionales en el chat con `#file:.ai/primitives/skills/add-rest-endpoint.md`.
3. Con instrucciones per-file, se pueden aplicar reglas especificas por lenguaje automaticamente.

## Limitaciones conocidas

- `.github/copilot-instructions.md` no soporta frontmatter.
- Limite de tamano no documentado oficialmente — mantener conciso.
- CLI Copilot tiene su propio sistema de instrucciones separado.

## Instalar

```bash
./.ai/adapters/copilot/install.sh
```

## Documentacion oficial

- https://docs.github.com/copilot/customizing-copilot/adding-custom-instructions-for-github-copilot
- https://github.blog/changelog/2025-11-12-copilot-code-review-and-coding-agent-now-support-agent-specific-instructions/
