# Adapter: Google Antigravity

Google Antigravity es un IDE agentico de Google basado en VSCode fork, lanzado el 18 de noviembre de 2025.
Implementa tres superficies: Editor (coding sincronico), Manager (orquestacion de agentes autonomos) y browser integration.

> Confianza: MEDIA — producto lanzado noviembre 2025, documentacion oficial parcial.
> No confundir con Gemini Code Assist (plugin para VS Code/JetBrains) ni con Jules (agente asincrono en cloud VM).

## Archivos que usa este adapter

| Archivo | Proposito | Prioridad |
|---|---|---|
| `GEMINI.md` (root) | Instrucciones especificas de Antigravity | Alta (mayor prioridad) |
| `AGENTS.md` (root) | Cross-tool compat, leido desde v1.20.3 (marzo 2026) | Media (deferido a GEMINI.md en conflictos) |
| `.agent/rules/*.md` | Reglas adicionales organizadas por concern | Adicional |
| `.gemini/antigravity/brain/` | Knowledge base generada automaticamente (NO editar) | Auto-generado |

## Jerarquia de reglas

1. `GEMINI.md` — mayor prioridad para reglas especificas de Antigravity
2. `AGENTS.md` — compartido con otros tools (Codex, Claude Code); deferido a GEMINI.md
3. `.agent/rules/*.md` — reglas adicionales por concern

## Conflicto conocido con Gemini CLI

Antigravity Global Rules y Gemini CLI ambos escriben a `~/.gemini/GEMINI.md`, causando
conflictos de configuracion entre las dos herramientas.
Issue: https://github.com/google-gemini/gemini-cli/issues/16058

Si usas ambas herramientas, gestionarlos manualmente o elegir una como principal.

## Como instalar via Antigravity UI

```bash
# Via CLI (si disponible)
mkdir -p .agent/rules
touch GEMINI.md
# O via Customizations panel > + Global / + Workspace
```

## Instalar con este adapter

```bash
./.ai/adapters/antigravity/install.sh
```

Esto crea `GEMINI.md` y `.agent/rules/architecture.md`. No modifica `AGENTS.md` (ya creado).

## Formato

Markdown plano — sin frontmatter especial. Antigravity lee los archivos como instrucciones directas.

## Skills system

Antigravity tiene un skills system separado documentado en Codelabs.
Ver: https://codelabs.developers.google.com/getting-started-with-antigravity-skills

## Limitaciones conocidas

- Documentacion oficial escasa (herramienta lanzada noviembre 2025).
- `.gemini/antigravity/brain/` se genera automaticamente — no editar manualmente.
- Jules (agente asincrono) tarda 2-5 min por sesion, corre en cloud VM (no local).
- Estructura interna del skills system no completamente especificada en docs oficiales.

## Documentacion oficial

- https://developers.googleblog.com/build-with-google-antigravity-our-new-agentic-development-platform/
- https://codelabs.developers.google.com/getting-started-google-antigravity
- https://codelabs.developers.google.com/getting-started-with-antigravity-skills
- https://antigravity.codes/blog/user-rules
