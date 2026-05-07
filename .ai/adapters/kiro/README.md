# Adapter: Kiro (AWS)

Kiro es el IDE agentico de AWS (lanzado agosto 2025). Lee steering files desde `.kiro/steering/*.md` con frontmatter YAML de activacion.

## Archivos que usa este adapter

| Archivo | Proposito |
|---|---|
| `.kiro/steering/product.md` | Contexto del producto, `inclusion: always` |
| `.kiro/steering/tech.md` | Stack tecnologico, `inclusion: always` |
| `.kiro/steering/structure.md` | Layout y arquitectura, `inclusion: fileMatch` para Java |
| `.kiro/hooks/` | Hooks de Kiro (eventos de archivo, agente, specs) |
| `.kiro/specs/` | Especificaciones generadas por el IDE (no crear manualmente) |

## Frontmatter de inclusion

```yaml
---
inclusion: always           # incluido en todos los contextos
# O:
inclusion: fileMatch
filePatterns:
  - "src/**/*.java"
  - "src/**/*.ts"
# O:
inclusion: manual           # solo invocacion explicita
---
```

## Foundation steering files (convencion recomendada por Kiro)

- `product.md` — proposito, usuarios, features, objetivos
- `tech.md` — frameworks, librerias, herramientas, constraints
- `structure.md` — organizacion de archivos, naming, patrones de imports

## Hooks de Kiro

Configurados en `.kiro/hooks/*.kiro.hook`. Eventos disponibles:
- File events: `fileCreated`, `fileSaved`, `fileDeleted`
- Agent lifecycle: `agentSpawn`, `userPromptSubmit`, `preToolUse`, `postToolUse`, `agentStop`
- Spec task events: `preTaskExecution`, `postTaskExecution`

Los hooks reciben eventos en JSON via STDIN.

## Como Kiro consume las primitivas

1. Kiro lee `.kiro/steering/` al abrir el workspace.
2. Steering con `inclusion: always` aplica a todos los contextos.
3. Steering con `inclusion: fileMatch` aplica cuando archivos coincidentes estan en contexto.
4. Las primitivas de `.ai/` se referencian desde los steering files.
5. Specs de Kiro (`.kiro/specs/`) son el mecanismo nativo de planning — generados por el IDE.

## Limitaciones conocidas

- Steering global en `~/.kiro/steering/` no se versiona con el proyecto.
- Specs en `.kiro/specs/` son generadas por el IDE — no crear manualmente.
- Hooks requieren que el IDE este corriendo (no son hooks de git).

## Instalar

```bash
./.ai/adapters/kiro/install.sh
```

## Documentacion oficial

- https://kiro.dev/docs/steering/
- https://kiro.dev/docs/hooks/
- https://kiro.dev/docs/getting-started/first-project/
