# 23 — Política de versión de Go

## Versión objetivo

Go 1.26.2 (verificado 2026-05-07, fuente: https://go.dev/blog/go1.26).

Último patch estable: 1.26.2 (publicado 2026-04-07), que incluye fixes de seguridad
en compilador, runtime y paquetes de la librería estándar.

## Por qué esta versión

- **Estado equivalente a LTS estable**: Go sigue una cadencia de release semestral (febrero
  y agosto). Go 1.26 salió en febrero de 2026 y recibirá patches de seguridad hasta que
  salga Go 1.28 (agosto 2027). Es la versión recomendada actualmente para servicios
  productivos.
- **Mejoras de toolchain y performance**: Go 1.26 continúa los refinamientos en PGO
  (profile-guided optimization), el iterador range-over-function (estabilizado en 1.22+)
  y latencia mejorada del garbage collector — todo relevante para un servicio de 150 TPS, p99 < 300ms.
- **Compatibilidad con dependencias del proyecto**: charmbracelet/bubbletea 1.3.x,
  charmbracelet/lipgloss 1.1.x, twmb/franz-go 1.21.x y coder/websocket 1.8.x declaran
  `go 1.21` o menor como mínimo, así que compilan limpio en 1.26.
- **Sin breaking changes**: aplica la garantía de compatibilidad de Go 1. Todo código que
  compilaba en 1.21 compila igual en 1.26.

## Cómo instalar

### Recomendado: goenv

```bash
brew install goenv
goenv install 1.26.2
goenv global 1.26.2
```

Para configurar por proyecto (ej.: repo que todavía requiere 1.21):

```bash
echo "1.26.2" > .go-version
goenv local 1.26.2
```

### Alternativa: brew directo

```bash
brew install go
```

Esto instala la última versión disponible en Homebrew, que puede ir atrás del release
oficial. Usalo solo si no necesitás mantener múltiples versiones de Go.

### Vía setup del proyecto

```bash
./setup.sh --only languages
```

El script de setup detecta goenv y llama a `goenv install 1.26.2 && goenv global 1.26.2`.
Si goenv no está, cae a `brew install go`.

## Por qué goenv sobre brew

`brew install go` reemplaza el binario global de Go. Si tenés servicios en distintas
versiones de Go (ej.: un servicio legacy en Go 1.21 y este proyecto en 1.26), brew hace
difícil cambiar. goenv permite:

- Versión por proyecto vía archivo `.go-version` (commiteado al repo).
- Swap a nivel shell: `goenv global 1.26.2` sin tocar otros proyectos.
- Misma ergonomía que `jenv` (Java), `nvm` (Node) o `sdkman` (ecosistema JVM).

## Target de go.mod

`cli/risk-smoke/go.mod` declara `go 1.26`. Esto significa:

- El módulo requiere toolchain Go 1.26+ para compilar.
- `go build` con un toolchain más viejo va a fallar con un error claro.
- Tanto CI como los scripts de setup local apuntan a 1.26.2.

## Referencia de cadencia de releases

Go sigue un calendario semestral predecible:

| Versión | Release | Fin de soporte de seguridad |
|---------|---------|------------------------|
| 1.25    | Ago 2025 | Feb 2027 |
| 1.26    | Feb 2026 | Ago 2027 |
| 1.27    | Ago 2026 (esperado) | Feb 2028 |

Fuente: https://go.dev/doc/devel/release

## Principio de diseño clave

> "Go cambia rapido. Tener un version manager por proyecto evita el caos de devs con
> versiones distintas. Es el mismo argumento que para Java con jenv o sdkman. Cada
> repo declara su version en `.go-version`; el CI lo respeta; no hay sorpresas."
