# .ai/ — Sistema de primitivas IDE-agnosticas

Este directorio contiene el sistema de primitivas que permite a CUALQUIER agente IA (Claude Code, Cursor, Windsurf, Copilot, Codex, opencode, Kiro, etc.) trabajar sobre este repo sin tener que leer el codebase completo para entender que pasa.

---

## Como lo lee cada IDE

| IDE | Archivo que leer primero |
|---|---|
| Claude Code | `../CLAUDE.md` → importa `../AGENTS.md` |
| Cursor | `../.cursor/rules/*.mdc` (automatico) |
| Windsurf | `../.windsurfrules` (automatico) |
| GitHub Copilot | `../.github/copilot-instructions.md` (automatico) |
| Codex CLI | `../AGENTS.md` (automatico) |
| opencode | `../AGENTS.md` + `.opencode/agents.md` |
| Kiro | `.kiro/instructions.md` (automatico) |
| Cualquier otro | `../AGENTS.md` |

---

## Estructura

```
.ai/
├── README.md                         # este archivo
├── primitives/
│   ├── skills/                       # 30 skills atomicos, single-purpose
│   ├── rules/                        # 12 reglas cross-cutting
│   ├── workflows/                    # 8 procedimientos multi-paso
│   └── hooks/                        # 5 hooks para harnesses
├── adapters/                         # un adapter por IDE
│   ├── claude-code/                  # README + install.sh
│   ├── cursor/                       # README + install.sh (genera .mdc)
│   ├── windsurf/                     # README + install.sh (genera .windsurfrules)
│   ├── copilot/                      # README + install.sh (genera copilot-instructions.md)
│   ├── codex/                        # README + install.sh (symlink AGENTS.md)
│   ├── antigravity/                  # README + install.sh (placeholder)
│   ├── opencode/                     # README + install.sh
│   └── kiro/                         # README + install.sh
├── context/
│   ├── architecture.md               # arquitectura completa con mermaid
│   ├── poc-inventory.md              # inventario de PoCs con estado
│   ├── decisions-log.md              # 11 ADRs
│   ├── glossary.md                   # 30+ terminos del dominio
│   ├── stack.md                      # versiones exactas de todo el stack
│   ├── engram.md                     # como usar Engram MCP en este proyecto
│   └── exploration-state.md          # estado actual de la exploración técnica
└── scripts/
    ├── engram-bootstrap.sh           # referencia para cargar contexto Engram
    └── verify-primitives.sh          # verifica que el sistema esta completo
```

---

## Instalar adapters

```bash
# Todos:
for ide in claude-code cursor windsurf copilot codex opencode kiro; do
    ./.ai/adapters/$ide/install.sh
done

# Uno especifico:
./.ai/adapters/cursor/install.sh
```

---

## Verificar el sistema

```bash
./.ai/scripts/verify-primitives.sh
```

Verifica: counts (skills>=25, rules>=12, workflows>=8, hooks>=5), frontmatter valido, adapters completos, archivos raiz existentes.

---

## Principios de diseno

1. **Sin duplicacion**: adapters traducen las primitivas al formato del IDE, no las copian.
2. **Autocontenido**: cada skill/rule/workflow puede ser ejecutado por un agente sin ir al codigo.
3. **Idempotente**: los `install.sh` de cada adapter pueden correrse multiples veces sin efectos negativos.
4. **Extensible**: agregar un nuevo IDE siguiendo el workflow `wire-new-ide.md`.
