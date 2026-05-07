---
title: "28 -- Soporte cross-platform: Mac, Linux, Windows"
tags: [devops, platform, setup, bash]
---

# 28 -- Soporte cross-platform: Mac, Linux, Windows

## Resumen

| Plataforma | Soporte | Notas |
|---|---|---|
| macOS (Apple Silicon + Intel) | Nativo completo | Target principal de desarrollo. OrbStack disponible para k8s rápido. |
| Linux (Ubuntu, Fedora, Arch, etc.) | Nativo completo | Sin OrbStack -- usa k3d. |
| Windows nativo (cmd.exe / PowerShell) | NO soportado | Los scripts son bash. |
| Windows vía WSL2 | Completo | Comportamiento idéntico a Linux. |

## Por qué Mac/Linux nativo y Windows vía WSL2

El repo tiene 70+ scripts shell. Reescribirlos a PowerShell duplica la superficie de
mantenimiento y crea brechas de paridad de comportamiento imposibles de cerrar del todo
(process substitution, manejo de señales, semántica de pipes). WSL2 entrega un Linux
casi nativo en Windows con integración Docker completa. Ese es el trade-off correcto en
2026 para un proyecto pesado en Java/Go/Docker.

## Setup por plataforma

### macOS

```bash
./nx setup
# Detecta brew, instala todo vía brew + cask. ~5 min en la primera corrida.
```

Requiere Homebrew. Si falta, `./nx setup` ofrece instalarlo.

### Linux (Debian/Ubuntu)

```bash
./nx setup
# Detecta apt, instala Java 21, Go, Docker, kubectl, helm, k3d.
```

### Linux (Fedora/RHEL)

```bash
./nx setup
# Detecta dnf automáticamente.
```

### Linux (Arch)

```bash
./nx setup
# Detecta pacman automáticamente.
```

### Windows

1. Instalar WSL2 + Ubuntu 22.04 desde Microsoft Store.
2. Dentro de WSL2: `git clone <repo> && cd <repo>`.
3. `./nx setup` -- idéntico al path de Linux nativo.
4. Para Docker: instalar Docker Desktop en Windows con integración WSL2 habilitada.

cmd.exe y PowerShell no están soportados ni se planea soportarlos.

## Problemas de compatibilidad: conocidos y resueltos

### `sed -i` BSD vs GNU

Mac trae BSD sed que requiere `sed -i ''` (string sufijo vacío). Linux trae GNU sed que
requiere `sed -i` (sin sufijo). Scripts que usaban la forma BSD fallan silenciosamente
en Linux.

Resultado de la auditoría: no se encontraron patrones `sed -i ''` en los scripts actuales.
Confirmado portable entre Mac y Linux.

Estrategia si aparece el patrón: usar `sed -i.bak 'EXPR' file && rm file.bak` que funciona
en ambos, o ramificar:

```bash
if sed --version 2>/dev/null | grep -q GNU; then
  sed -i 's|foo|bar|g' file
else
  sed -i '' 's|foo|bar|g' file
fi
```

### `top -l 1` vs `/proc/loadavg`

`top` de Mac acepta `-l N` para N muestras (single-shot). `top` de Linux no tiene este
flag. Para métricas de CPU y RAM el test runner usa `os.getloadavg()` de Python (stdlib
Mac/Linux) y `/proc/meminfo` con fallback `sysctl hw.memsize`. Ningún script shell usa
`top -l 1`.

### Versión de Bash: el default de Mac es 3.2

macOS trae bash 3.2 (GPLv2). Las features de Bash 4+ (arrays asociativos, `wait -n`,
`mapfile`) no están disponibles en el shell del sistema.

Resolución: `nx`, `scripts/test-all.sh` y `scripts/setup/setup.sh` detectan la versión de
bash al inicio y hacen re-exec bajo brew bash (4+) cuando está disponible:

```bash
if [ "${BASH_VERSINFO[0]}" -lt 4 ]; then
  BREW_BASH=""
  if command -v brew >/dev/null 2>&1; then
    BREW_BASH="$(brew --prefix 2>/dev/null)/bin/bash"
  fi
  if [ -n "$BREW_BASH" ] && [ -x "$BREW_BASH" ]; then
    exec "$BREW_BASH" "$0" "$@"
  fi
fi
```

El guard `command -v brew` evita que el code path de Linux llame a un binario inexistente.
Sin el guard, `brew --prefix` en Linux escribe a stderr y devuelve un string vacío,
haciendo que `BREW_BASH` quede como `/bin/bash` -- potencialmente un re-exec infinito en
sistemas donde `/bin/bash` también es 3.x.

### `stat -f%z` (BSD) vs `stat -c%s` (GNU)

BSD stat usa `-f%z` para tamaño de archivo; GNU stat usa `-c%s`. Ningún script actual
usa stat para tamaños de archivo. Si hay que agregar este patrón:

```bash
if stat -f%z /dev/null 2>/dev/null; then
  size=$(stat -f%z "$file")   # BSD/Mac
else
  size=$(stat -c%s "$file")   # GNU/Linux
fi
```

### `date -j -f` (BSD) vs `date -d` (GNU)

BSD date usa `-j -f FORMAT` para parsing; GNU date usa `-d STRING`. Ningún script actual
parsea fechas con estos flags. Si hace falta, usar Python3 para aritmética de fechas
(`python3 -c "from datetime import datetime..."`) ya que Python3 es dependencia dura del
proyecto.

### Paths de Homebrew en scripts de PoC

Varios scripts de PoC referencian `/opt/homebrew/opt/openjdk/bin/java` directamente.
Esos scripts son atajos de desarrollo, no parte del path principal de test/CI. Incluyen
lógica de fallback (ver `bench/scripts/competition.sh`) que chequea el path hardcodeado
solo cuando existe:

```bash
if [[ -x "/opt/homebrew/opt/openjdk/bin/java" ]]; then
  JAVA="/opt/homebrew/opt/openjdk/bin/java"
fi
```

En Linux esos paths simplemente no existen y el fallback (`command -v java`) toma el
control. Esto es aceptable para scripts de PoC en tiempo de desarrollo.

## Verificación

```bash
./nx setup --verify
# Reporta cada tool requerida: versión, estado (instalada / falta / desactualizada).
```

Auditoría cross-platform vía la herramienta de cobertura:

```bash
./nx audit cli
# Incluye chequeo "cross-platform aware" con % de scripts usando patrones portables.
```

## Node.js: gestionado vía fnm

Para el SDK TypeScript usamos **fnm** (Fast Node Manager), no `brew install node`
ni descargas directas. fnm provee:

- Switching automático con `.node-version` por proyecto.
- Mac y Linux con un único path de instalación (Homebrew o instalador shell).
- Activación por shell (no contamina el sistema).

Setup:

```bash
brew install fnm                                         # macOS
curl -fsSL https://fnm.vercel.app/install | bash         # Linux

# Activar en ~/.zshrc o ~/.bashrc:
eval "$(fnm env --use-on-cd)"

fnm install --lts && fnm default lts-latest
```

`./nx setup --verify` detecta fnm y verifica que node/npm resuelvan después de
activar. Ver `27-test-runner.md` (sección "Node.js via fnm") para detalles del
wrapper `scripts/lib/with-fnm.sh` que activa fnm en jobs del test runner.

## Referencia de diseño

> "Cross-platform es una decisión arquitectónica, no un nice-to-have. Mac/Linux
> nativo más Windows vía WSL2 es la elección correcta en 2026. Reescribir 70+
> scripts bash a PowerShell duplica la superficie de mantenimiento y hace
> imposible garantizar paridad de comportamiento. Si tu CI corre Linux y tus devs
> corren Mac, WSL2 cierra el loop sin reescribir. El invariante clave: todo
> script testeado en Mac funciona idénticamente en el runner Linux de CI."
