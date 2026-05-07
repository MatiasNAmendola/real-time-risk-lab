# IDE Conventions Research — Verified 2026-05-07

> Research conducted via WebSearch on 2026-05-07.
> Purpose: ground-truth spec for adapters in `.ai/adapters/<ide>/`.
> Do NOT modify adapters based on this file directly — pass it to the adapter-fix agent.

---

## Claude Code

**Vendor**: Anthropic  
**Latest version**: Tracked via changelog at code.claude.com  
**Last verified**: 2026-05-07  
**Official docs**: https://code.claude.com/docs/en/

### Convención de archivos en el repo
- Path canónico: `CLAUDE.md` (root), `.claude/CLAUDE.md` (project-scoped)
- Subdirectory overrides: additional `CLAUDE.md` files in subdirectories append context
- Sub-agents: `.claude/agents/*.md`
- Slash commands / skills: `.claude/commands/*.md` o `.claude/skills/*.md` (custom slash commands merged into skills)
- Formato: Markdown con YAML frontmatter (en sub-agents y skills)
- Múltiples archivos soportados: sí (jerarquía root + subdirs + user global `~/.claude/CLAUDE.md`)
- Hot reload: sí (cambios en CLAUDE.md se leen al siguiente turno)

### Frontmatter o metadata esperada (sub-agents)
```yaml
---
name: code-reviewer
description: Reviews code for quality and best practices. Use when asked to review or audit code.
tools: Read, Glob, Grep
model: sonnet
---
```

Campos:
- `name` (requerido): identificador del sub-agent
- `description` (requerido): cuándo invocarlo (usado por el modelo para routing)
- `tools` (opcional): allowlist de herramientas separadas por coma
- `disallowedTools` (opcional): denylist alternativa a `tools`
- `model` (opcional): override de modelo; resolución = env var > parámetro de invocación > frontmatter > modelo del contexto padre

### Cómo el IDE descubre/carga los archivos
- `CLAUDE.md` en root se carga automáticamente en contexto
- `.claude/agents/*.md` disponibles para invocación por el modelo principal
- Prioridad: proyecto > usuario global (`~/.claude/`)
- MCP servers configurados en `.claude/settings.json` bajo clave `mcpServers`

### Hooks soportados
Configurados en `.claude/settings.json` (o `~/.claude/settings.json` para global).

Eventos disponibles:
- `PreToolUse` — antes de ejecutar una herramienta
- `PostToolUse` — después de ejecutar una herramienta (con matchers: `Write`, `Edit`, `Bash`, etc.)
- `Notification` — cuando Claude emite una notificación
- `Stop` — cuando el agente termina

Ejemplo en `settings.json`:
```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          { "type": "command", "command": "echo 'file changed' >> $CLAUDE_PROJECT_DIR/.log" }
        ]
      }
    ]
  }
}
```
Variable disponible: `$CLAUDE_PROJECT_DIR` para paths seguros.

### Slash commands o equivalentes
- Built-in: `/help`, `/clear`, `/compact`, `/model`, `/config`, etc.
- Custom: archivos `.claude/commands/<name>.md` o `.claude/skills/<name>.md`
- Plugins: pueden registrar comandos adicionales

### Sub-agents o skills atómicos
- Sub-agents: `.claude/agents/*.md` con frontmatter YAML
- Skills: `.claude/skills/*.md` — instrucciones para comportamientos reutilizables
- Plugins: directorio `.claude/plugins/` (estructura de plugin con `package.json`)

### MCP Servers
Configurados en `.claude/settings.json`:
```json
{
  "mcpServers": {
    "my-server": {
      "command": "node",
      "args": ["path/to/server.js"],
      "env": {}
    }
  }
}
```

### Limitaciones conocidas
- `CLAUDE.md` tiene límite de contexto (archivos muy grandes pueden ser truncados)
- Sub-agents no pueden invocar otros sub-agents directamente (solo el modelo principal puede)
- `model` en frontmatter de sub-agent puede ser sobreescrito por parámetros de invocación

### Comandos de "instalación" en proyecto
```bash
# No hay CLI install command específico para CLAUDE.md
# Se crea manualmente o via /init
claude /init   # genera CLAUDE.md con estructura básica
```

### Verificado en
- https://code.claude.com/docs/en/sub-agents
- https://code.claude.com/docs/en/settings
- https://docs.anthropic.com/en/docs/claude-code/sub-agents

### Confianza
**Alta** — docs oficiales de Anthropic directamente, URLs verificadas.

---

## Cursor

**Vendor**: Anysphere  
**Latest version**: v0.50+ (2026); versión 2.2 mencionada en notas de cambio de rules  
**Last verified**: 2026-05-07  
**Official docs**: https://docs.cursor.com/

### CAMBIO IMPORTANTE (2025 → 2026)
> Cursor migró de `.cursorrules` (archivo único, legacy) a `.cursor/rules/*.mdc` (directorio con archivos individuales). A partir de v2.2, **nuevas reglas se crean en `.cursor/rules/`**. `.cursorrules` sigue funcionando por compatibilidad hacia atrás pero es formato legacy 2024.

### Convención de archivos en el repo
- Path canónico ACTUAL: `.cursor/rules/<nombre>.mdc`
- Path legacy (NO usar para nuevos proyectos): `.cursorrules` (root, archivo único)
- Formato: Markdown con frontmatter YAML (extensión `.mdc`)
- Múltiples archivos soportados: sí (cada archivo en `.cursor/rules/` es una regla independiente)
- Hot reload: sí

### Frontmatter o metadata esperada
```yaml
---
description: "Short description of the rule's purpose (shown in UI)"
globs: "src/**/*.ts,src/**/*.tsx"
alwaysApply: false
---
# Rule Title

Main rule content with instructions...
```

Campos:
- `description` (string): mostrado en la UI y usado por el agente para decidir si aplicar la regla
- `globs` (string): patrones de archivos separados por coma; requerido para auto-attachment
- `alwaysApply` (boolean): si `true`, incluida en cada chat session independientemente de globs

### Cómo el IDE descubre/carga los archivos
- Si `alwaysApply: true` → incluida en todos los contextos
- Si `alwaysApply: false` → el agente (Composer) evalúa la `description` y decide si aplicar según el contexto actual
- Reglas con `globs` se adjuntan automáticamente cuando los archivos coincidentes están en contexto
- Cursor lee el directorio `.cursor/rules/` al iniciar workspace

### Hooks soportados
- No hay hooks explícitos en el sentido de Claude Code
- `.cursor/rules/` puede contener reglas que se activan por archivo/contexto (auto-attachment vía globs)

### Slash commands o equivalentes
- `@skill-name` o `@<nombre-de-regla>` para referenciar reglas manualmente en Composer
- Built-in: `/edit`, `/generate`, `@codebase`, `@docs`, `@web`, etc.

### Sub-agents o skills atómicos
- No hay sub-agents nativos al estilo Claude Code
- Se simulan con reglas específicas en `.cursor/rules/`

### Limitaciones conocidas
- El formato `.cursorrules` (legacy) ignora frontmatter — todo el contenido se trata como instrucción plana
- Reglas muy largas pueden afectar performance; máximo recomendado no documentado oficialmente
- `alwaysApply: true` en muchas reglas puede saturar el contexto

### Comandos de "instalación" en proyecto
```bash
# Crear directorio y primera regla
mkdir -p .cursor/rules
# Luego crear archivos .mdc manualmente o desde la UI de Cursor (Settings > Rules)
```

### Verificado en
- https://forum.cursor.com/t/optimal-structure-for-mdc-rules-files/52260
- https://deepwiki.com/fbrbovic/cursor-rule-framework/2.1-mdc-format-specification
- https://gist.github.com/bossjones/1fd99aea0e46d427f671f853900a0f2a (notas de cambio v2.2)

### Confianza
**Alta** — múltiples fuentes confirman el formato `.mdc` como canónico en 2025-2026. Cambio desde `.cursorrules` bien documentado en foros y changelogs oficiales.

---

## Windsurf

**Vendor**: Codeium (adquirido por OpenAI en 2025)  
**Latest version**: Wave 8+ (2026)  
**Last verified**: 2026-05-07  
**Official docs**: https://docs.windsurf.com/

### CAMBIO IMPORTANTE (pre-Wave 8 → Wave 8+)
> Antes de Wave 8: archivo único `.windsurfrules` en root del proyecto.
> Desde Wave 8: directorio `.windsurf/rules/` con múltiples archivos. `.windsurfrules` sigue siendo **soportado por compatibilidad hacia atrás** pero es menos flexible.

### Convención de archivos en el repo
- Path canónico ACTUAL: `.windsurf/rules/<nombre>.md` (directorio)
- Path legacy: `.windsurfrules` (root, archivo único, aún soportado)
- Memorias auto-generadas: `~/.codeium/windsurf/memories/` (local, no versionable)
- Formato: Markdown plano (rules), con frontmatter YAML opcional para modo de activación
- Múltiples archivos soportados: sí (desde Wave 8)
- Hot reload: sí

### Frontmatter o metadata esperada (rules en `.windsurf/rules/`)
```yaml
---
trigger: always_on
# O:
trigger: glob
glob: "src/**/*.ts"
# O:
trigger: manual
---
# Rule Content
```

Modos de activación:
- `always_on`: aplica siempre
- `glob`: aplica cuando coincide patrón de archivo
- `manual`: solo cuando el usuario la invoca explícitamente

### Cómo el IDE descubre/carga los archivos
- Cascade (el agente de Windsurf) lee `.windsurf/rules/` y `.windsurfrules`
- Jerarquía: global (settings del IDE) > workspace rules > sistema
- Memorias: generadas automáticamente por Cascade durante conversación, stored en `~/.codeium/windsurf/memories/`
- Límites: workspace rule files → 12,000 chars cada uno; global rules → 6,000 chars

### Hooks soportados
- No hay hooks explícitos en formato de archivo; comportamiento automático vía Cascade
- Cascade puede autogenerar memorias según el contexto de la conversación

### Slash commands o equivalentes
- Comandos built-in de Cascade: no hay slash commands documentados explícitamente como archivos
- Se puede pedir a Cascade crear/actualizar memorias: "remember that..."

### Sub-agents o skills atómicos
- No hay sub-agents nativos documentados en el formato de archivos
- Cascade es el único agente, configurable vía rules

### Limitaciones conocidas
- Memorias son locales (no versionables, en `~/.codeium/windsurf/memories/`)
- Límite de 12,000 chars por archivo de regla workspace
- `.windsurfrules` legacy no soporta frontmatter de activación

### Comandos de "instalación" en proyecto
```bash
mkdir -p .windsurf/rules
# Crear archivos .md con frontmatter de trigger
```

### Verificado en
- https://docs.windsurf.com/windsurf/cascade/memories
- https://design.dev/guides/windsurf-rules/
- https://www.claudemdeditor.com/windsurfrules-guide

### Confianza
**Alta** — docs oficiales de Windsurf verificados, cambio Wave 8 confirmado por múltiples fuentes.

---

## GitHub Copilot

**Vendor**: GitHub (Microsoft)  
**Latest version**: Feature set a noviembre 2025 (agent-specific instructions añadidas)  
**Last verified**: 2026-05-07  
**Official docs**: https://docs.github.com/copilot/customizing-copilot/adding-custom-instructions-for-github-copilot

### Convención de archivos en el repo
- Path canónico principal: `.github/copilot-instructions.md`
- Path per-file/per-language (2025+): `.github/instructions/<nombre>.instructions.md`
- Formato: Markdown plano (`.github/copilot-instructions.md` sin frontmatter)
- Formato per-file: Markdown con frontmatter YAML (`.instructions.md`)
- Múltiples archivos soportados: sí (`.github/instructions/` directory)
- Hot reload: sí

### Frontmatter o metadata esperada (`.instructions.md` files)
```yaml
---
applyTo: "app/models/**/*.rb"
# Múltiples patrones:
# applyTo: "**/*.ts,**/*.tsx"
# Excluir agentes específicos:
excludeAgent: "code-review"
# excludeAgent: "coding-agent"
---
# Instruction content in Markdown
```

Campos:
- `applyTo` (glob string): archivos/directorios a los que aplica; separados por coma para múltiples
- `excludeAgent` (string): `"code-review"` o `"coding-agent"` para excluir agentes específicos

Sin frontmatter en `.github/copilot-instructions.md` (aplica a todo el repositorio globalmente).

### Cómo el IDE descubre/carga los archivos
- `.github/copilot-instructions.md`: cargado automáticamente para todas las solicitudes en el contexto del repo
- `.github/instructions/*.instructions.md`: aplicados según `applyTo` glob matching con archivos en contexto
- Prioridad: personal instructions > repository instructions > organization instructions
- Disponible en VS Code, JetBrains, GitHub.com, CLI

### Hooks soportados
- No hay hooks de archivos explícitos
- Copilot Code Review puede tener instrucciones específicas vía `excludeAgent`

### Slash commands o equivalentes
- Chat: `/explain`, `/fix`, `/tests`, `/doc`
- Editor: inline suggestions (no slash commands explícitos)
- Extensions: pueden registrar comandos adicionales

### Sub-agents o skills atómicos
- Copilot coding agent (preview): puede recibir instrucciones específicas vía `.instructions.md` con `excludeAgent: "coding-agent"` para excluirlo
- Code review agent: `excludeAgent: "code-review"` para excluirlo de instrucciones específicas

### Limitaciones conocidas
- `.github/copilot-instructions.md` no soporta frontmatter — todo es instrucción plana
- Archivos `.instructions.md` requieren glob en `applyTo` para aplicarse selectivamente
- Límite de tamaño de instrucciones no documentado oficialmente
- CLI Copilot tiene su propio sistema de instrucciones separado

### Comandos de "instalación" en proyecto
```bash
mkdir -p .github/instructions
touch .github/copilot-instructions.md
# Luego agregar instrucciones específicas por lenguaje:
# .github/instructions/typescript.instructions.md
# .github/instructions/python.instructions.md
```

### Verificado en
- https://docs.github.com/copilot/customizing-copilot/adding-custom-instructions-for-github-copilot
- https://docs.github.com/en/copilot/reference/custom-instructions-support
- https://github.blog/changelog/2025-11-12-copilot-code-review-and-coding-agent-now-support-agent-specific-instructions/

### Confianza
**Alta** — docs oficiales de GitHub verificados, feature `excludeAgent` confirmado (noviembre 2025).

---

## Codex CLI

**Vendor**: OpenAI  
**Latest version**: Activo en 2026 (docs actualizadas mayo 2026)  
**Last verified**: 2026-05-07  
**Official docs**: https://developers.openai.com/codex/

### ORIGEN HISTÓRICO IMPORTANTE
> Codex (OpenAI) fue quien originó la convención `AGENTS.md` como archivo de instrucciones por proyecto. Este formato fue luego adoptado por otros tools (Antigravity, etc.). El archivo canónico es `AGENTS.md` en el **root del repo**, no dentro de un subdirectorio.

### Convención de archivos en el repo
- Path canónico: `AGENTS.md` (root del repo)
- Lookup order por directorio: `AGENTS.override.md` → `AGENTS.md` → `TEAM_GUIDE.md` → `.agents.md`
- Configuración global: `~/.codex/config.toml`
- Configuración por proyecto (MCP scoped): `.codex/config.toml`
- Formato: Markdown plano (AGENTS.md), TOML (config)
- Múltiples archivos soportados: sí (lookup cascade por directorio)
- Hot reload: sí para AGENTS.md

### Frontmatter o metadata esperada
`AGENTS.md` no tiene frontmatter — es Markdown plano con instrucciones.

`~/.codex/config.toml` o `.codex/config.toml`:
```toml
# MCP server configuration
[mcp_servers.my-server]
command = "node path/to/server.js"
# O HTTP:
# url = "https://my-server.example.com/mcp"

# Model preferences
model = "o3"

# Approval mode
approval_mode = "auto"
```

### Cómo el IDE descubre/carga los archivos
- Codex busca AGENTS.md en el directorio actual y parents (lookup cascade)
- `AGENTS.override.md` tiene mayor precedencia (para overrides locales)
- MCP servers definidos en `config.toml` se lanzan automáticamente al iniciar sesión
- Configuración de proyecto (`.codex/config.toml`) solo cargada en proyectos "trusted"

### Hooks soportados
- No hay hooks explícitos de archivo
- MCP servers pueden proveer hooks via protocolo MCP

### Slash commands o equivalentes
- CLI: `codex mcp` para gestionar MCP servers
- No hay slash commands interactivos documentados

### Sub-agents o skills atómicos
- No hay sub-agents nativos en formato de archivos
- Se logra vía MCP servers que exponen herramientas

### MCP Support
```toml
# ~/.codex/config.toml
[mcp_servers.filesystem]
command = "npx @modelcontextprotocol/server-filesystem /path/to/allowed"

[mcp_servers.remote-server]
url = "https://my-mcp-server.example.com/mcp"
```
Soporta: stdio (local) y streaming HTTP (remoto).

### Limitaciones conocidas
- `AGENTS.md` en root puede ser confundido con el `AGENTS.md` de otros tools (Antigravity lo lee también)
- `.codex/config.toml` solo se carga en proyectos marcados como "trusted" (seguridad)
- No hay equivalente a hooks de Claude Code para automatización de eventos

### Comandos de "instalación" en proyecto
```bash
# Crear AGENTS.md en root
touch AGENTS.md
# Configurar MCP (opcional)
codex mcp add my-server --command "node server.js"
# O editar ~/.codex/config.toml manualmente
```

### Verificado en
- https://developers.openai.com/codex/guides/agents-md
- https://developers.openai.com/codex/config-reference
- https://developers.openai.com/codex/mcp
- https://github.com/openai/codex/blob/main/docs/config.md

### Confianza
**Alta** — docs oficiales de OpenAI directamente, URLs específicas verificadas.

---

## Google Antigravity

**Vendor**: Google  
**Latest version**: v1.20.3+ (lanzado noviembre 2025, marzo 2026 con soporte AGENTS.md)  
**Last verified**: 2026-05-07  
**Official docs**: https://developers.googleblog.com/build-with-google-antigravity-our-new-agentic-development-platform/

### Descripción del producto
Antigravity es un IDE agentic lanzado el 18 de noviembre de 2025, basado en VSCode fork. Implementa arquitectura de tres superficies: Editor (coding síncrono), Manager (orquestación de agentes autónomos) y browser integration para testing automatizado. **Es diferente de Gemini Code Assist** (plugin para VS Code/JetBrains) y de Jules (agente asíncrono en cloud VM). Los tres coexisten bajo el paraguas de Google AI.

### Convención de archivos en el repo
- Path canónico principal: `GEMINI.md` (global en `~/.gemini/GEMINI.md`, o en project root)
- Reglas de workspace: `.agent/rules/` (directorio)
- AGENTS.md cross-tool: `AGENTS.md` en root (soportado desde v1.20.3, marzo 2026)
- Brain/knowledge base: `.gemini/antigravity/brain/` (directorio, generado automáticamente)
- Configuración MCP: `~/.gemini/antigravity/mcp_config.json`
- Formato: Markdown plano (sin frontmatter especial)
- Múltiples archivos soportados: sí (`.agent/rules/` permite organizar por concern)
- Hot reload: sí

### Frontmatter o metadata esperada
Sin frontmatter especial — archivos Markdown planos.

### Jerarquía de reglas
1. `GEMINI.md` — mayor prioridad para reglas específicas de Antigravity
2. `AGENTS.md` — compartido con otros tools (Cursor, Claude Code); deferido a GEMINI.md en conflictos
3. `.agent/rules/*.md` — reglas adicionales organizadas por concern

### Cómo el IDE descubre/carga los archivos
- Antigravity lee `~/.gemini/GEMINI.md` (global) y `GEMINI.md` en project root
- Lee `AGENTS.md` en root (soporte añadido v1.20.3)
- Lee todos los archivos en `.agent/rules/`
- Genera y actualiza `.gemini/antigravity/brain/` automáticamente como knowledge base persistente

### Nota de conflicto conocido
> Issue #16058 en gemini-cli: Antigravity Global Rules y Gemini CLI Global Context **ambos escriben** a `~/.gemini/GEMINI.md`, causando conflictos de configuración entre las dos herramientas.

### Hooks soportados
- Skills system: codelabs documentan "Authoring Google Antigravity Skills"
- No hay hooks de eventos explícitos como Kiro o Claude Code documentados en sources encontrados

### Slash commands o equivalentes
- No documentados específicamente en las sources encontradas

### Sub-agents o skills atómicos
- Skills system: ver https://codelabs.developers.google.com/getting-started-with-antigravity-skills
- Jules puede integrarse para tareas asíncronas (se lanza como agente separado en cloud VM)

### Limitaciones conocidas
- Conflicto de configuración con Gemini CLI en `~/.gemini/GEMINI.md` (issue #16058)
- `.gemini/antigravity/brain/` se genera automáticamente — no debe editarse manualmente
- Documentación oficial escasa comparada con Claude Code o Cursor (herramienta lanzada noviembre 2025)
- Jules (agente asíncrono) tarda 2-5 minutos por sesión, corre en cloud VM (no local)

### Comandos de "instalación" en proyecto
```bash
# Crear directorios de reglas
mkdir -p .agent/rules
touch GEMINI.md
# O via UI de Antigravity: Customizations panel > + Global / + Workspace
```

### Verificado en
- https://developers.googleblog.com/build-with-google-antigravity-our-new-agentic-development-platform/
- https://codelabs.developers.google.com/getting-started-google-antigravity
- https://github.com/google-gemini/gemini-cli/issues/16058
- https://antigravity.codes/blog/user-rules
- https://codelabs.developers.google.com/getting-started-with-antigravity-skills

### Confianza
**Media** — producto relativamente nuevo (noviembre 2025). Docs oficiales parciales; información completada con Codelabs y blog de Google Developers. Skills system documentado en Codelabs pero estructura interna de archivos no completamente especificada en docs oficiales.

---

## opencode

**Vendor**: opencode-ai (open source, Go-based)  
**Latest version**: Activo 2026  
**Last verified**: 2026-05-07  
**Official docs**: https://opencode.ai/docs/

### Descripción
opencode es un CLI TUI (Terminal User Interface) para coding AI, escrito en Go. Es open source (`github.com/opencode-ai/opencode`). **No es de Charmbracelet** (Charmbracelet hizo "Crush", herramienta diferente en el mismo ecosistema terminal). opencode tiene su propio proyecto separado.

### Convención de archivos en el repo
- Config global: `~/.config/opencode/opencode.json`
- Config de proyecto (mayor precedencia): `opencode.json` en root del proyecto
- Subdirectorios de config: `~/.config/opencode/` contiene `agents/`, `commands/`, `modes/`, `plugins/`, `skills/`, `tools/`, `themes/`
- Formato: JSON
- Múltiples archivos soportados: sí (global + proyecto; proyecto tiene precedencia)
- Hot reload: no documentado

### Frontmatter o metadata esperada
No hay frontmatter — configuración en `opencode.json`:
```json
{
  "provider": "anthropic",
  "model": "claude-sonnet-4-6",
  "theme": "default",
  "mcp": {
    "servers": {
      "filesystem": {
        "command": ["npx", "@modelcontextprotocol/server-filesystem", "/allowed/path"]
      }
    }
  },
  "agents": {},
  "permissions": {}
}
```

### Cómo el IDE descubre/carga los archivos
- Carga primero `~/.config/opencode/opencode.json` (global)
- Luego `opencode.json` en project root (override/merge)
- Documentación menciona también config "remote" pero project config tiene highest precedence

### Hooks soportados
- No documentados explícitamente como hooks de eventos

### Slash commands o equivalentes
- TUI interactivo con comandos propios; no hay convención de archivos para slash commands personalizados documentada

### Sub-agents o skills atómicos
- `~/.config/opencode/agents/` — para configurar agentes custom
- `~/.config/opencode/skills/` — para skills
- Estructura interna de estos directorios no completamente especificada en docs encontradas

### MCP Support
```json
{
  "mcp": {
    "servers": {
      "my-server": {
        "command": ["node", "server.js"],
        "env": {}
      },
      "remote": {
        "url": "https://my-mcp-server.com/mcp"
      }
    }
  }
}
```
Soporta: stdio (local) y HTTP (remoto).

### Limitaciones conocidas
- Estructura interna de `agents/`, `skills/`, `tools/` en `~/.config/opencode/` no completamente documentada en sources encontradas
- Documentación oficial menos exhaustiva que Claude Code o Codex
- Posible confusión: "Crush" de Charmbracelet es herramienta diferente en el mismo espacio terminal AI

### Comandos de "instalación" en proyecto
```bash
# Instalar opencode (Go)
# Ver github.com/opencode-ai/opencode para instrucciones
touch opencode.json  # crear config de proyecto
```

### Verificado en
- https://opencode.ai/docs/config/
- https://opencode.ai/docs/mcp-servers/
- https://opencode.ai/docs/cli/
- https://github.com/opencode-ai/opencode

### Confianza
**Media** — docs oficiales existen en opencode.ai pero incompletas en algunos aspectos (estructura interna de `agents/` y `skills/` subdirs). Config principal (`opencode.json`) bien documentada.

---

## Kiro

**Vendor**: AWS (Amazon)  
**Latest version**: Activo en 2026; lanzado agosto 2025  
**Last verified**: 2026-05-07  
**Official docs**: https://kiro.dev/docs/

### Convención de archivos en el repo
- Steering workspace: `.kiro/steering/*.md`
- Steering global: `~/.kiro/steering/*.md`
- Hooks: `.kiro/hooks/*.kiro.hook`
- Specs: `.kiro/specs/` (generados por el IDE)
- Formato: Markdown con frontmatter YAML (steering), JSON (hooks events via STDIN)
- Múltiples archivos soportados: sí (directorio entero)
- Hot reload: sí

### Frontmatter o metadata esperada (steering files)
```yaml
---
inclusion: always
# O:
inclusion: fileMatch
filePatterns:
  - "src/**/*.ts"
  - "src/**/*.tsx"
# O:
inclusion: manual
---
# Steering content in Markdown
```

Modos de inclusión:
- `always` — incluido en todos los contextos
- `fileMatch` — incluido cuando archivos coincidan con `filePatterns`
- `manual` — solo cuando el usuario lo invoca explícitamente

### Foundation steering files (convención recomendada)
- `product.md` — propósito del producto, usuarios, features, objetivos
- `tech.md` — frameworks, bibliotecas, herramientas, constraints técnicas
- `structure.md` — organización de archivos, naming conventions, patrones de imports

### Cómo el IDE descubre/carga los archivos
- Kiro lee `~/.kiro/steering/` (global) y `.kiro/steering/` (workspace)
- Global steering aplica a todos los workspaces
- Workspace steering aplica solo al workspace actual
- Hooks en `.kiro/hooks/*.kiro.hook` monitoreados por el IDE automáticamente

### Hooks soportados
Configurados en `.kiro/hooks/<name>.kiro.hook`.

Eventos disponibles:
- **File events**: `fileCreated`, `fileSaved`, `fileDeleted`
- **Agent lifecycle**: `agentSpawn`, `userPromptSubmit`, `preToolUse`, `postToolUse`, `agentStop`
- **Spec task events**: `preTaskExecution`, `postTaskExecution`
- **Manual trigger**: ejecución manual

Hooks reciben eventos en JSON via STDIN. Pueden ejecutar un prompt de agente o un shell command.

```json
// Ejemplo de evento recibido por hook
{
  "event": "fileSaved",
  "file": "src/components/Button.tsx",
  "workspace": "/path/to/project"
}
```

### Slash commands o equivalentes
- Kiro trabaja con "specs" y "steering" como mecanismos principales
- No hay slash commands de archivos documentados; interacción via chat del IDE

### Sub-agents o skills atómicos
- Specs: `.kiro/specs/` — transforma ideas en planes de implementación (Requirements → Design → Tasks)
- Specs tienen tres fases: Requirements (user stories con criterios EARS), Design (arquitectura técnica), Tasks (breakdown de implementación)
- CLI Kiro también soporta custom agents con configuration reference en `kiro.dev/docs/cli/custom-agents/`

### Limitaciones conocidas
- Specs son específicas de Kiro — no hay equivalente en otros IDEs
- Hooks requieren que el IDE esté corriendo para monitorear eventos (no son hooks de git)
- Global steering en `~/.kiro/steering/` no es versionable con el proyecto

### Comandos de "instalación" en proyecto
```bash
mkdir -p .kiro/steering .kiro/hooks
# Crear steering foundation files
touch .kiro/steering/product.md
touch .kiro/steering/tech.md
touch .kiro/steering/structure.md
```

### Verificado en
- https://kiro.dev/docs/steering/
- https://kiro.dev/docs/hooks/
- https://kiro.dev/docs/getting-started/first-project/
- https://kiro.dev/blog/automate-your-development-workflow-with-agent-hooks/

### Confianza
**Alta** — docs oficiales de kiro.dev verificadas, hooks y steering bien documentados.

---

## Aider

**Vendor**: Paul Gauthier / aider-chat (open source)  
**Latest version**: Activo en 2026 (docs actualizadas)  
**Last verified**: 2026-05-07  
**Official docs**: https://aider.chat/docs/

### Convención de archivos en el repo
- Config YAML: `.aider.conf.yml` (root del repo o home dir)
- Conventions file: cualquier archivo Markdown (típicamente `CONVENTIONS.md`) referenciado desde `.aider.conf.yml`
- `.aiderignore`: análogo a `.gitignore` para excluir archivos del repo map
- Formato: YAML (config), Markdown plano (conventions)
- Múltiples archivos soportados: sí (lista de `read:` en config)
- Hot reload: no (config se lee al inicio)

### Frontmatter o metadata esperada
`.aider.conf.yml`:
```yaml
# Modelo principal
model: claude-sonnet-4-6

# Siempre cargar estos archivos como read-only context
read:
  - CONVENTIONS.md
  - docs/architecture.md

# Modo de edición
edit-format: diff

# Architect mode
architect: true
editor-model: claude-sonnet-4-6

# Auto commits
auto-commits: true

# Otros
dark-mode: true
```

### Cómo el IDE descubre/carga los archivos
- Aider busca `.aider.conf.yml` en: home dir → root del repo (el más cercano tiene precedencia)
- Archivos listados en `read:` se cargan como read-only context en cada sesión
- Repo map se genera automáticamente analizando el repo git (clases, funciones, call signatures)
- Repo map ayuda al LLM a entender la codebase sin cargar todos los archivos

### Repo Map
- Generado automáticamente al iniciar aider en un repo git
- Incluye: lista de archivos, símbolos clave (clases, funciones) con tipos y call signatures
- Actualizado incrementalmente al modificar archivos
- Se puede deshabilitar con `--no-map`

### Hooks soportados
- No hay hooks de eventos explícitos como Claude Code o Kiro
- `--test-cmd` permite ejecutar tests automáticamente tras cada edición

### Slash commands o equivalentes
- Chat modes: `/code` (default), `/ask` (solo preguntas), `/architect` (propone cambios sin editar), `/context` (gestión de contexto)
- Built-in: `/add <file>`, `/drop <file>`, `/model <name>`, `/undo`, `/git <cmd>`

### Sub-agents o skills atómicos
- Architect mode: el modelo "architect" propone, un "editor model" implementa
- No hay sub-agents en formato de archivos

### Limitaciones conocidas
- `.aider.conf.yml` no tiene frontmatter — es YAML puro
- No hay convención de directorio (solo archivos sueltos)
- Configuración no es por-proyecto de forma exclusiva (home dir también aplica)
- `CONVENTIONS.md` es convención de la comunidad, no un path hardcodeado

### Comandos de "instalación" en proyecto
```bash
pip install aider-chat
# En el repo:
touch .aider.conf.yml
touch CONVENTIONS.md
aider  # inicia con repo map automático
```

### Verificado en
- https://aider.chat/docs/config/aider_conf.html
- https://aider.chat/docs/repomap.html
- https://aider.chat/docs/usage/modes.html
- https://aider.chat/docs/usage/conventions.html

### Confianza
**Alta** — docs oficiales de aider.chat verificadas directamente.

---

## Continue

**Vendor**: continue.dev (open source)  
**Latest version**: Activo 2026; formato `config.yaml` como nuevo estándar  
**Last verified**: 2026-05-07  
**Official docs**: https://docs.continue.dev/

### CAMBIO IMPORTANTE (2024 → 2025/2026)
> Continue migró de `config.json` a `config.yaml` como formato canónico. `config.json` está **deprecated** pero documentado para compatibilidad. Además, hay un `.continuerc.json` para overrides por proyecto (no por lenguaje).

### Convención de archivos en el repo
- Config global canónica (NUEVO): `~/.continue/config.yaml`
- Config global legacy (deprecated): `~/.continue/config.json`
- Override de proyecto: `.continuerc.json` (en root del repo)
- Prompt files (reemplazo de slash commands): `~/.continue/prompts/*.prompt` o `.continue/prompts/*.prompt`
- Formato principal: YAML (`config.yaml`), JSON (legacy/override)
- Múltiples archivos soportados: sí (global + override de proyecto)
- Hot reload: sí

### Frontmatter o metadata esperada
`.continuerc.json` (override de proyecto):
```json
{
  "mergeBehavior": "merge",
  "models": [],
  "contextProviders": [],
  "slashCommands": []
}
```
`mergeBehavior`: `"merge"` (default) aplica encima de config.json global; `"overwrite"` reemplaza.

`config.yaml` (nuevo formato):
```yaml
models:
  - provider: anthropic
    model: claude-sonnet-4-6
    apiKey: ${ANTHROPIC_API_KEY}

context:
  providers:
    - name: code
    - name: docs
    - name: diff
    - name: open

mcpServers:
  - name: filesystem
    command: npx
    args: ["@modelcontextprotocol/server-filesystem", "/allowed"]

rules:
  - Prefer TypeScript over JavaScript
  - Follow the existing code style
```

### Cómo el IDE descubre/carga los archivos
- Continue (VS Code extension) lee `~/.continue/config.yaml` al inicio
- `.continuerc.json` en root del proyecto se mergea encima del config global
- Context providers disponibles al tipear `@` en el chat
- MCP prompts se convierten en slash commands automáticamente

### Hooks soportados
- No hay hooks de eventos explícitos en formato de archivos

### Slash commands o equivalentes
- **DEPRECADO**: `slashCommands` array en `config.json`
- **NUEVO**: Prompt files en `~/.continue/prompts/` o `.continue/prompts/`
- MCP prompts (via mcpServers) se registran automáticamente como slash commands
- Built-in: `/edit`, `/comment`, `/share`, `/cmd`, `/commit`

### Sub-agents o skills atómicos
- Agents: compuestos de models + rules + tools (MCP servers) definidos en `config.yaml`
- No hay archivos separados por agente documentados en sources encontradas

### Context Providers
Referenciados con `@` en el chat:
- `@code` — símbolos y archivos del codebase
- `@docs` — documentación indexada
- `@diff` — cambios actuales en git
- `@open` — archivos abiertos en el editor
- `@web` — búsqueda web
- `@terminal` — output de terminal

### Limitaciones conocidas
- `config.json` está deprecated — nuevos setups deben usar `config.yaml`
- `slashCommands` en `config.json` está deprecated — usar prompt files
- `.continuerc.json` es JSON, no YAML (inconsistente con el nuevo `config.yaml`)
- `.continuerc.json` no soporta todas las opciones de config global

### Comandos de "instalación" en proyecto
```bash
# Instalar extensión de VS Code: continue.continue
# Config global se genera automáticamente en ~/.continue/config.yaml
# Override de proyecto:
touch .continuerc.json
mkdir -p .continue/prompts
```

### Verificado en
- https://docs.continue.dev/customize/overview
- https://docs.continue.dev/customize/deep-dives/configuration
- https://docs.continue.dev/reference (config.yaml reference)
- https://docs.continue.dev/reference/json-reference (config.json deprecated)
- https://docs.continue.dev/customize/slash-commands

### Confianza
**Alta** — docs oficiales de continue.dev verificadas, migración a `config.yaml` confirmada.

---

## Cross-IDE Comparison

| IDE | File path | Format | Multi-file | Frontmatter | MCP | Hooks | Slash | Confianza |
|---|---|---|---|---|---|---|---|---|
| **Claude Code** | `CLAUDE.md`, `.claude/agents/*.md` | Markdown + YAML FM | Sí | YAML (agents/skills) | Sí (`settings.json`) | Sí (PostToolUse, PreToolUse, Stop) | Sí (`.claude/commands/*.md`) | Alta |
| **Cursor** | `.cursor/rules/*.mdc` (legacy: `.cursorrules`) | Markdown + YAML FM (.mdc) | Sí | YAML (`description`, `globs`, `alwaysApply`) | No nativo | No (solo globs auto-attach) | `@rule-name` | Alta |
| **Windsurf** | `.windsurf/rules/*.md` (legacy: `.windsurfrules`) | Markdown + YAML FM | Sí (Wave 8+) | YAML (`trigger`, `glob`) | No nativo | No | No | Alta |
| **GitHub Copilot** | `.github/copilot-instructions.md`, `.github/instructions/*.instructions.md` | Markdown plano / YAML FM | Sí | YAML (`applyTo`, `excludeAgent`) | No | No | `/explain`, `/fix`, `/tests` | Alta |
| **Codex CLI** | `AGENTS.md` (root), `~/.codex/config.toml` | Markdown plano / TOML | Sí (cascade) | No | Sí (config.toml) | No | No | Alta |
| **Antigravity** | `GEMINI.md`, `.agent/rules/*.md`, `AGENTS.md` | Markdown plano | Sí | No | Sí (`mcp_config.json`) | Skills system | No documentado | Media |
| **opencode** | `opencode.json` (root), `~/.config/opencode/opencode.json` | JSON | Sí (global+project) | No | Sí (JSON config) | No | No | Media |
| **Kiro** | `.kiro/steering/*.md`, `.kiro/hooks/*.kiro.hook` | Markdown + YAML FM / JSON events | Sí | YAML (`inclusion`, `filePatterns`) | No nativo | Sí (file/agent/spec events) | No | Alta |
| **Aider** | `.aider.conf.yml`, `CONVENTIONS.md` | YAML / Markdown | Sí (lista `read:`) | No | No | No | `/code`, `/ask`, `/architect` | Alta |
| **Continue** | `~/.continue/config.yaml`, `.continuerc.json` | YAML / JSON | Sí (global+override) | No | Sí (mcpServers) | No (archivos) | Prompt files, MCP prompts | Alta |

---

## Action Items para los Adapters

### `.ai/adapters/cursor/`
- **CRÍTICO**: Confirmar que el adapter genera archivos `.mdc` en `.cursor/rules/` y **NO** `.cursorrules` — ese formato es legacy 2024 y está deprecado desde v2.2.
- El `install.sh` debe crear el directorio `.cursor/rules/` si no existe.
- Cada rule file debe incluir frontmatter con al menos `description` y `alwaysApply: false` (o `globs` para auto-attach).
- Si el adapter creó `.cursorrules`, renombrarlo o convertirlo a `.cursor/rules/*.mdc`.

### `.ai/adapters/windsurf/`
- **CRÍTICO**: Verificar que el adapter usa `.windsurf/rules/` (formato Wave 8+) y NO `.windsurfrules` como único archivo.
- Si el adapter creó `.windsurfrules` únicamente, migrar a `.windsurf/rules/<nombre>.md`.
- Agregar frontmatter `trigger: always_on` (o `glob`) a los archivos de reglas.
- Recordar límite: 12,000 chars por archivo de workspace rule.

### `.ai/adapters/codex/`
- **CRÍTICO**: El path canónico es `AGENTS.md` en el **root del repo**, NO `.codex/AGENTS.md` o subdirectorio. Si el adapter creó un subdirectorio incorrecto, moverlo al root.
- Config de MCP va en `~/.codex/config.toml` (global) o `.codex/config.toml` (proyecto, solo trusted).
- El lookup order es: `AGENTS.override.md` → `AGENTS.md` → `TEAM_GUIDE.md` → `.agents.md`.

### `.ai/adapters/antigravity/`
- **TODO**: Marcar con confianza MEDIA; producto lanzado noviembre 2025 con documentación oficial parcial.
- Path principal: `GEMINI.md` en project root (o `.agent/rules/*.md` para organizar por concern).
- Desde v1.20.3 (marzo 2026) también lee `AGENTS.md` en root (cross-tool compatible).
- Advertir sobre el conflicto con Gemini CLI en `~/.gemini/GEMINI.md` (issue #16058).
- Ver docs oficiales: https://codelabs.developers.google.com/getting-started-google-antigravity
- Skills system separado: https://codelabs.developers.google.com/getting-started-with-antigravity-skills

### `.ai/adapters/opencode/`
- Config de proyecto: `opencode.json` en root (no subdirectorio).
- Config global: `~/.config/opencode/opencode.json`.
- El proyecto tiene mayor precedencia que el global.
- MCP servers se configuran dentro de `opencode.json` bajo clave `mcp.servers`.
- Documentar que `agents/`, `skills/` son subdirectorios de `~/.config/opencode/` (global), no del proyecto.

### `.ai/adapters/copilot/`
- Confirmar que el adapter crea `.github/copilot-instructions.md` para instrucciones globales del repo.
- Para instrucciones per-lenguaje/per-tarea, usar `.github/instructions/<nombre>.instructions.md` con frontmatter `applyTo`.
- Si se usan instructions files, recordar que `.github/copilot-instructions.md` NO lleva frontmatter.
- Feature `excludeAgent` disponible desde noviembre 2025 para excluir code-review o coding-agent.

### `.ai/adapters/kiro/`
- Steering files en `.kiro/steering/` con frontmatter `inclusion: always|fileMatch|manual`.
- Foundation files recomendados: `product.md`, `tech.md`, `structure.md`.
- Hooks en `.kiro/hooks/*.kiro.hook` — los eventos son JSON via STDIN.
- Specs generadas en `.kiro/specs/` por el IDE (no crear manualmente).
- Steering global en `~/.kiro/steering/` no se versiona; solo versionar `.kiro/steering/`.

### `.ai/adapters/continue/`
- **CRÍTICO**: Si el adapter usa `config.json` como path canónico, actualizarlo a `config.yaml` — ese es el nuevo formato oficial.
- `slashCommands` en config.json está deprecado; usar prompt files en `.continue/prompts/*.prompt`.
- Override de proyecto: `.continuerc.json` (JSON, con `mergeBehavior: "merge"`).
- MCP servers se configuran en `config.yaml` bajo `mcpServers:`.
- Context providers se referencian con `@` en chat; configurarlos en `context.providers`.

### `.ai/adapters/aider/`
- Config en `.aider.conf.yml` (YAML puro, sin frontmatter).
- Conventions se cargan via `read: [CONVENTIONS.md]` en el config YAML.
- No hay directorio `.aider/` canónico — archivos en root o home dir.
- Architect mode: configurar `architect: true` y `editor-model:` en `.aider.conf.yml`.

### `.ai/adapters/claude-code/`
- Sub-agents en `.claude/agents/*.md` con frontmatter YAML (`name`, `description`, `tools`, `model`).
- Skills en `.claude/commands/*.md` o `.claude/skills/*.md`.
- Hooks en `.claude/settings.json` bajo clave `hooks` (eventos: PreToolUse, PostToolUse, Stop, Notification).
- MCP servers en `.claude/settings.json` bajo `mcpServers`.
- Usar `$CLAUDE_PROJECT_DIR` en comandos de hooks para paths confiables.

---

*Generado por investigación WebSearch — 2026-05-07*  
*Uso: input para agente de fix de adapters en `.ai/adapters/<ide>/`*

---

## Action items applied 2026-05-07

Cambios aplicados en `.ai/adapters/` basados en este research:

- **cursor**: Ya estaba correcto (genera `.cursor/rules/*.mdc` con frontmatter MDC). README actualizado con nota explícita de que NO se genera `.cursorrules` legacy y link adicional a foro Cursor.

- **windsurf**: `install.sh` reescrito para generar AMBOS formatos: `.windsurf/rules/*.md` con frontmatter `trigger: always_on/glob` (Wave 8+) Y `.windsurfrules` como fallback legacy pre-Wave 8. README actualizado con tabla de archivos, frontmatter de activación y límite de 12,000 chars.

- **kiro**: `install.sh` reescrito para usar los tres foundation files recomendados (`product.md`, `tech.md`, `structure.md`) en `.kiro/steering/` con frontmatter correcto `inclusion: always/fileMatch`. Se eliminó el archivo incorrecto `.kiro/instructions.md` (no es el path canónico). README actualizado con hooks y steering frontmatter.

- **antigravity**: Reemplazado el placeholder honesto con información real del research. `install.sh` ahora crea `GEMINI.md` en root y `.agent/rules/architecture.md`. README documenta jerarquía (GEMINI.md > AGENTS.md > .agent/rules/), soporte de AGENTS.md desde v1.20.3, conflicto con Gemini CLI (issue #16058), confianza MEDIA.

- **codex**: `install.sh` ya era correcto (symlink `.codex/AGENTS.md -> ../AGENTS.md`). README actualizado con cascade lookup order (`AGENTS.override.md` → `AGENTS.md` → `TEAM_GUIDE.md` → `.agents.md`) y documentación de `.codex/config.toml`.

- **copilot**: `install.sh` ya era correcto (genera `.github/copilot-instructions.md`). README actualizado para documentar el soporte de `.github/instructions/*.instructions.md` con frontmatter `applyTo` y `excludeAgent` (desde noviembre 2025).

- **opencode**: `install.sh` reescrito para generar `opencode.json` en root del proyecto (no `.opencode/agents.md` — ese path no es el canónico). README actualizado con formato JSON, nota de confianza MEDIA sobre estructura de `agents/skills/` subdirs.

- **continue** (nuevo): Adapter creado desde cero. `install.sh` genera `.continuerc.json` (override de proyecto con `mergeBehavior: merge`) y `.continue/prompts/atdd-feature.prompt` (prompt file como reemplazo de `slashCommands` deprecated). README documenta migración `config.json` → `config.yaml` y modelo de prompt files.

- **aider**: No existía adapter, no se creó (no era requerimiento de esta tarea; el research lo documenta pero no se solicitó el adapter).

- **claude-code**: No requirió cambios (ya tenía adapter correcto).
