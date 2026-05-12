---
title: Confidentiality Hashing — SHA-256 blocklist sin literales
tags: [concept, security, pattern/secrets]
created: 2026-05-12
source_archive: docs/31-confidentiality-scanner.md (migrado 2026-05-12, Fase 3 Round 2, split)
---

# Confidentiality Hashing

## Por qué una blocklist literal es un anti-patrón

Una blocklist que guarda términos prohibidos en plaintext es el peor patrón para
enforcement de confidencialidad. El scanner que se supone que protege términos
sensibles termina exponiéndolos en código fuente, historial de commits, logs y
artefactos de CI visibles para todo contributor y herramienta de auditoría.

"Una blocklist literal en código es el peor patrón de seguridad: el scanner
que protege la confidencialidad expone los términos protegidos. Hashear es
trivial, pero pensar el threat model por una línea es lo que separa código
que parece seguro de código que es seguro."

## Diseño: hashing SHA-256

### Cómo funciona

1. Un término se agrega una sola vez: `--add "<term>"`. El scanner lo normaliza
   (lowercase, strip, remover `-_`) y guarda solo el digest hex SHA-256. El
   plaintext nunca se escribe a disco por el scanner.
2. El archivo de blocklist (`.ai/blocklist.sha256`) está gitignored y vive solo
   en la máquina del desarrollador o en una variable de entorno de CI respaldada
   por un secrets manager.
3. Al momento del scan se extrae cada palabra de cada archivo escaneado, se
   normaliza y se hashea. Si el hash matchea una entrada de la blocklist, el
   archivo se marca. El output muestra solo el prefijo de 12 caracteres del hash
   — nunca la palabra original.

### Por qué SHA-256 alcanza acá

El threat model es leakage accidental por desarrolladores, no ataques
adversariales de diccionario. SHA-256 sube la barra lo suficiente para el caso
común mientras mantiene la implementación en stdlib de Python con cero
dependencias.

### Layout de archivos

```
.ai/
  blocklist.sha256.example   # commiteado, vacío, documenta el formato
  blocklist.sha256            # gitignored, tus hashes reales van acá
  scripts/
    confidentiality-scanner.py
    test_confidentiality_scanner.py
```

## Threat model

El threat model es leakage accidental por desarrolladores, no ataques adversariales. Casos cubiertos:

- Un dev commitea accidentalmente el nombre de un cliente, proyecto interno, o código de área.
- Un log de CI expone un término sensible en plaintext.
- Un artefacto de build incluye el nombre real de un proyecto confidencial.

Casos NO cubiertos (fuera del scope):

- Un insider determinado que tiene acceso al archivo de hashes y quiere revertir la blocklist.
- Ataques adversariales de diccionario contra la blocklist (ver Limitaciones).

## Limitaciones

### Ataques de diccionario

Como SHA-256 es rápido y determinístico, un atacante que sabe que estás
protegiendo una lista corta de nombres propios conocidos (nombres de empresa,
códigos de proyecto) puede enumerar candidatos probables y matchear hashes en
segundos. La blocklist no es secreta frente a un insider determinado. Protege
contra leakage accidental, no contra adversarios determinados que ya tienen
acceso al archivo de hashes.

### Falsos positivos

La extracción de palabras usa un regex simple (`[a-zA-Z][a-zA-Z0-9-_]{3,29}`).
Palabras comunes de diccionario que casualmente hasheen a una entrada de la
blocklist dispararían un falso positivo. En la práctica esto es astronómicamente
improbable, pero es no-cero para términos muy cortos. Usar términos de 8+
caracteres cuando se pueda.

### Sensibilidad a límites de palabra

La normalización quita guiones y underscores, así que `<WORD>`, `<word>`,
`<wo-rd>` y `<wo_rd>` hashean idénticamente. Esto es intencional: captura
variaciones comunes. Sin embargo, si el término prohibido está embebido dentro
de un token más largo (ej.: `"prefix<WORD>suffix"`) sin límites de palabra, el
regex no lo va a extraer como candidato standalone.

### Archivos binarios y generados

El scanner solo procesa archivos con extensiones de texto conocidas (`.md`,
`.py`, `.java`, `.go`, etc.) y saltea `build/`, `node_modules/`, `.git/`, etc.
Los artefactos binarios no se escanean.

## Anti-patrones

### NO guardar plaintext en la blocklist

```
# MAL — expone los términos que el scanner debe proteger
COMPANY_NAME
INTERNAL_REPO
PROJECT_X
```

### NO loggear las palabras matcheadas

Cualquier output que incluya el plaintext matcheado anula el propósito. El
scanner solo emite el prefijo de 12 caracteres del hash y le pide al reviewer
que inspeccione el archivo manualmente.

### NO hardcodear la blocklist en archivos de entorno de CI

Si la blocklist tiene que vivir en CI, inyectarla desde un secrets manager en
runtime en lugar de commitearla. El archivo `.ai/blocklist.sha256` no debería
aparecer nunca en ningún artefacto commiteado, log o capa de cache.

### NO saltear el scanner para archivos "confiables"

No existe tal cosa como un archivo confiable en un repositorio multi-contributor.
Todo archivo de texto es un vector potencial de leakage.

## Related

- [[Secrets-PII-Protection]]
- [[Risk-Platform-Overview]]
