---
name: licensing
applies_to: ["**/pom.xml", "**/docker-compose*.yml", "**/go.mod"]
priority: medium
---

# Regla: licensing

## Stack de este proyecto — licencias

| Componente | Licencia | Restriccion |
|---|---|---|
| Java / OpenJDK | GPL v2 + Classpath Exception | Libre para uso |
| Vert.x 5 | Apache 2.0 | Libre para uso y redistribucion |
| Maven | Apache 2.0 | Libre |
| Karate | MIT | Libre |
| Cucumber-JVM | MIT | Libre |
| JaCoCo | EPL 2.0 | Libre (con atribucion) |
| Redpanda | BSL → Apache 2.0 (4 anos) | Libre para uso propio, restricciones de competencia |
| Postgres 16 | PostgreSQL License | Libre |
| Valkey 8 | BSD 3-Clause | Libre |
| OpenTelemetry Java | Apache 2.0 | Libre |
| Helm | Apache 2.0 | Libre |
| ArgoCD | Apache 2.0 | Libre |
| kube-prometheus-stack | Apache 2.0 | Libre |
| External Secrets | Apache 2.0 | Libre |
| **MinIO** | **AGPL 3.0** | **Solo dev/test. No redistribuir como producto sin licencia comercial** |
| **OpenBao** | **MPL 2.0** | Copyleft debil. Modificaciones al codigo de OpenBao deben publicarse |
| Bubble Tea | MIT | Libre |
| Go | BSD 3-Clause | Libre |

## Reglas de uso

### MinIO (AGPL)

- Solo usar en `poc/` y desarrollo local.
- No incluir MinIO en un producto SaaS sin licencia comercial.
- Las dependencias de tu codigo de aplicacion NO se contagian del AGPL de MinIO (MinIO es un servicio separado, no una libreria linkeada).

### OpenBao (MPL 2.0)

- Usar como servicio (servidor). MPL se aplica a modificaciones del codigo fuente de OpenBao mismo.
- Puedes usar la API de OpenBao sin restricciones en tu aplicacion.
- Si modificas el codigo fuente de OpenBao: debes publicar esas modificaciones.

### Stack default (Apache 2.0 / MIT / BSD)

- Sin restricciones relevantes para uso en PoCs y demostraciones.
- Atribucion requerida por MIT/BSD: no eliminar headers de copyright en codigo copiado.

## Agregar nueva dependencia

Antes de agregar una dependencia al pom.xml o go.mod:
1. Verificar licencia en Maven Central o la documentacion oficial.
2. Si es AGPL o GPL: consultar con el equipo antes de incluir.
3. Documentar en este archivo si es una licencia no-estandar.
