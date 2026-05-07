---
name: wire-new-ide
description: Como agregar soporte para un nuevo IDE al sistema de primitivas .ai/
steps: [research, create-adapter, create-install-sh, test, document]
---

# Workflow: wire-new-ide

## Cuando usar

Cuando se agrega un IDE/agente que no tiene adapter en `.ai/adapters/`.

## 1. Investigar el formato del IDE

Preguntas a responder antes de escribir una linea:
- Que archivo(s) lee el IDE automaticamente al abrir el repo? (ej. `.cursorrules`, `AGENTS.md`, `.windsurfrules`)
- Soporta multiples archivos o solo uno?
- Tiene frontmatter? Tiene formato especial?
- Soporta globs para aplicar reglas a archivos especificos?
- Tiene soporte de slash commands, skills, o workflows?
- Documentacion oficial: URL.

## 2. Crear el adapter

Estructura minima en `.ai/adapters/<ide>/`:
```
.ai/adapters/<ide>/
├── README.md       # descripcion completa del adapter
└── install.sh      # script idempotente de instalacion
```

### README.md debe incluir:
1. Que archivos crea/exige el IDE en el repo.
2. Como el IDE consume las primitivas.
3. Limitaciones conocidas.
4. Comando para instalar.
5. Link a docs oficiales.

### install.sh debe ser:
- Idempotente (se puede correr multiples veces sin efectos negativos).
- Verificar prerequisitos.
- Crear symlinks o copiar archivos desde `.ai/primitives/` al lugar que el IDE espera.
- No sobreescribir archivos del usuario sin preguntar.

## 3. Crear archivos especificos del IDE (si aplica)

Siguiendo el principio de NO duplicar: los archivos del IDE deben:
- Referenciar o importar desde `.ai/primitives/` cuando sea posible.
- Cuando el IDE no soporta imports: generar un archivo consolidado en el install.sh.

Ejemplos:
- Windsurf: concatenar rules en `.windsurfrules`.
- Copilot: un solo archivo `.github/copilot-instructions.md` con resumen + links.

## 4. Verificar

```bash
./.ai/adapters/<ide>/install.sh
# Verificar que los archivos esperados por el IDE existen
./.ai/scripts/verify-primitives.sh
```

## 5. Documentar en AGENTS.md

Agregar el IDE al mapa de adapters en `AGENTS.md` raiz.

## 6. Commit

```bash
git add .ai/adapters/<ide>/ <archivos-del-ide>
git commit -m "feat(adapter): add <ide> adapter"
```
