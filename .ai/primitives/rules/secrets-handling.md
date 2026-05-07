---
name: secrets-handling
applies_to: ["**/*.java", "**/*.yaml", "**/*.properties", "**/*.env", "**/docker-compose*.yml"]
priority: high
---

# Regla: secrets-handling

## Nunca

- Hardcodear secrets, passwords, tokens, API keys en codigo fuente.
- Commitear archivos `.env` con valores reales.
- Loguear secrets (ni siquiera parcialmente, excepto primeros 4 chars para debug).
- Poner secrets en variables de entorno en Dockerfile o docker-compose con valores reales de produccion.
- Secrets en URL parameters o query strings.

## En desarrollo local

- Usar archivos `.env.local` (en `.gitignore`) para valores locales.
- Credenciales de mocks: `test/test`, `minioadmin/minioadmin`, `root` (OpenBao dev) son seguras para dev.
- OpenBao dev mode en `poc/k8s-local`: `VAULT_TOKEN=root` es aceptable solo para demos locales.

## En k8s-local

- External Secrets Operator (ESO) con provider `kubernetes` o `vault` (OpenBao).
- El recurso `ExternalSecret` referencia un `SecretStore` configurado en `poc/k8s-local/addons/41-cluster-secret-store.yaml`.
- No crear `Secret` de Kubernetes con valores hardcodeados en YAML commiteado.

## En la aplicacion Java

```java
// Leer desde variable de entorno (inyectada por ESO o docker-compose)
String dbPassword = System.getenv("DB_PASSWORD");
if (dbPassword == null) throw new IllegalStateException("DB_PASSWORD not set");

// Nunca:
String dbPassword = "hardcoded-password-123"; // PROHIBIDO
```

## En Helm values

```yaml
# values.yaml (commiteado) — solo referencias, no valores:
database:
  password:
    secretName: risk-engine-db
    key: password

# El Secret es creado por ExternalSecret, no en values.yaml
```

## Verificacion

```bash
# Buscar posibles secrets hardcodeados
grep -r -i "password\s*=\s*['\"].\+['\"]" poc/ --include="*.java"
grep -r -i "secret\s*=\s*['\"].\+['\"]" poc/ --include="*.java"
grep -r "API_KEY\|SECRET_KEY\|TOKEN" poc/ --include="*.java" | grep -v "getenv\|System\.\|env\."
```
