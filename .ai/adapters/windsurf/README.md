# Adapter: Windsurf

Windsurf (Codeium/OpenAI) lee rules desde `.windsurf/rules/*.md` (Wave 8+) y tambien soporta `.windsurfrules` como fallback legacy.

## Archivos que usa este adapter

| Archivo | Proposito |
|---|---|
| `.windsurf/rules/00-project.md` | Context global, `trigger: always_on` |
| `.windsurf/rules/10-java-arch.md` | Reglas Java/arch, `trigger: glob` para `**/*.java` |
| `.windsurf/rules/20-testing.md` | Reglas ATDD, `trigger: glob` para `**/*.feature` |
| `.windsurfrules` (raiz) | Fallback para Windsurf pre-Wave 8 (sin frontmatter) |

Ambos formatos coexisten. Windsurf Wave 8+ usa `.windsurf/rules/`; versiones anteriores usan `.windsurfrules`.

## Frontmatter de activacion (Wave 8+)

```yaml
---
trigger: always_on          # aplica siempre
# O:
trigger: glob
glob: "src/**/*.java"       # aplica cuando archivos coincidan
# O:
trigger: manual             # solo invocacion explicita
---
```

## Limite de tamano

Cada archivo de rule en `.windsurf/rules/` tiene un limite de 12,000 caracteres.
`.windsurfrules` no tiene frontmatter de activacion — todo es instruccion plana.

## Como Windsurf consume las primitivas

1. Cascade (agente de Windsurf) lee `.windsurf/rules/` al abrir el workspace.
2. Rules con `trigger: always_on` se incluyen en todos los contextos.
3. Rules con `trigger: glob` se incluyen cuando archivos coincidentes estan en contexto.
4. Skills se acceden referenciando el archivo: `.ai/primitives/skills/`.

## Limitaciones conocidas

- Memorias auto-generadas por Cascade se guardan en `~/.codeium/windsurf/memories/` (local, no versionable).
- `.windsurfrules` legacy no soporta frontmatter de activacion — todo se aplica siempre.
- Limite de 12,000 chars por archivo de workspace rule (Wave 8+).

## Instalar

```bash
./.ai/adapters/windsurf/install.sh
```

## Documentacion oficial

- https://docs.windsurf.com/windsurf/cascade/memories
- https://docs.windsurf.com/ (Wave 8+ rules directory format)
