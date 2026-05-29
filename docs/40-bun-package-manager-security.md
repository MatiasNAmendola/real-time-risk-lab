# 40 — Bun package manager + lifecycle-script hardening

## Decisión

Si este repo usa tooling JavaScript/TypeScript, el package manager obligatorio es **Bun**.
No usar `npm`, `pnpm` ni `yarn` para instalar dependencias del repo.

Archivos fuente de verdad:

- `bunfig.toml` en la raíz del repo.
- `sdks/risk-client-typescript/bunfig.toml` para que el default aplique incluso cuando se corre desde el SDK.
- `sdks/risk-client-typescript/bun.lock` como lockfile canónico.
- `.npmrc` con `ignore-scripts=true` como defensa en profundidad si alguien corre npm por accidente.

## Configuración obligatoria

```toml
[install]
ignoreScripts = true
```

Impacto: `bun install` y `bun add` no ejecutan scripts lifecycle de paquetes ni de workspaces.
Esto bloquea hooks como:

- `preinstall`
- `install`
- `postinstall`
- `prepare`
- variantes `{pre|post}install` / `{pre|post}prepare` del proyecto o workspace

## Por qué

Los lifecycle scripts son comandos shell arbitrarios definidos por paquetes npm.
Sirven para compilar binarios nativos o preparar paquetes, pero también son un vector de malware/supply-chain attack.
La postura del repo es **deny-by-default**: instalar dependencias no debe ejecutar código de terceros.

Bun ya es más conservador que npm para dependencias transitivas, pero `install.ignoreScripts = true`
endurece aún más el comportamiento: no se ejecutan lifecycle scripts ni siquiera para el proyecto actual,
workspaces o `trustedDependencies`.

## Impacto revisado en el SDK TypeScript

Módulo auditado: `sdks/risk-client-typescript`.

- Scripts propios actuales: `build`, `test`, `test:integration`.
- No hay `prepare`, `preinstall`, `install` ni `postinstall` propios.
- En el `package-lock.json` previo, el único paquete marcado con install script era `fsevents@2.3.3`.
  Es dependencia opcional/macOS usada por toolchains de watch; no es necesaria para build/test normal del SDK.
- Con `ignoreScripts = true`, cualquier paquete futuro que requiera compilar binarios nativos vía `postinstall`
  va a fallar o quedar incompleto hasta que se audite explícitamente.

## Cómo trabajar

```bash
cd sdks/risk-client-typescript
bun install --frozen-lockfile
bun run build
bun run test -- --runInBand
bun run test:integration
```

Para agregar dependencias:

```bash
cd sdks/risk-client-typescript
bun add <paquete>
```

Después de `bun add`, revisar:

```bash
git diff -- package.json bun.lock bunfig.toml
```

Si una dependencia necesita lifecycle scripts, no habilitarlos globalmente. Primero:

1. justificar por qué es necesaria;
2. revisar su paquete y scripts publicados;
3. preferir alternativa sin scripts;
4. si no hay alternativa, documentar una excepción acotada y reproducible.

## Comandos prohibidos para mantenimiento del repo

```bash
npm install
npm ci
npm add
pnpm install
yarn install
```

`.npmrc` mitiga `npm install` accidental con `ignore-scripts=true`, pero el flujo soportado es Bun.
