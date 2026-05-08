---
name: refactor-to-enterprise-layout
intent: Refactorizar un modulo Java para que siga el layout canonico domain/application/infrastructure/cmd/config
inputs: [module_path, current_package_root]
preconditions:
  - El modulo compila actualmente
  - Tests pasan antes del refactor
postconditions:
  - Clases reorganizadas en domain/{entity,repository,usecase,service,rule}, application/{usecase/<aggregate>,mapper,dto}, infrastructure/{controller,consumer,repository,resilience,time}, cmd/, config/
  - Imports actualizados
  - Tests siguen pasando
  - Sin logica de negocio en infrastructure/
related_rules: [architecture-clean, clean-arch-boundaries, java-version, naming-conventions]
---

# Skill: refactor-to-enterprise-layout

## Pasos

1. **Snapshot antes**: `./gradlew test` verde, git commit.

2. **Mapear clases existentes a destino**:
   | Clase actual | Destino |
   |---|---|
   | `RiskDecision` (entidad) | `domain/entity/RiskDecision.java` |
   | `RiskRepository` (interfaz) | `domain/repository/RiskRepository.java` |
   | `EvaluateRiskUseCase` (interfaz) | `domain/usecase/EvaluateRiskUseCase.java` |
   | `EvaluateRiskUseCaseImpl` | `application/usecase/risk/EvaluateRiskUseCaseImpl.java` |
   | `RiskMapper` | `application/mapper/RiskMapper.java` |
   | `RiskRequest/Response DTO` | `application/dto/risk/` |
   | `HttpController` | `infrastructure/controller/RiskController.java` |
   | `PostgresRiskRepository` | `infrastructure/repository/RiskRepositoryPostgres.java` |
   | `MainVerticle` | `cmd/MainVerticle.java` |
   | `AppConfig` | `config/AppConfig.java` |

3. **Mover archivos** (IntelliJ: Refactor > Move, o `mv` + sed para imports):
   ```bash
   # Ejemplo: mover y actualizar imports
   find src -name "*.java" -exec sed -i 's/io.riskplatform.old.RiskDecision/io.riskplatform.domain.entity.RiskDecision/g' {} \;
   ```

4. **Verificar boundary violations**:
   ```bash
   # domain/ no debe importar de application/ o infrastructure/
   grep -r "import io.riskplatform.infrastructure" src/main/java/io/riskplatform/domain/ && echo "VIOLATION"
   grep -r "import io.riskplatform.application" src/main/java/io/riskplatform/domain/ && echo "VIOLATION"
   ```

5. **`./gradlew test`** verde de nuevo.

## Notas
- Hacer el refactor en commits atomicos (mover entidades, luego application, luego infrastructure).
- Si el PoC tiene tests que usan rutas de paquete, actualizarlos tambien.
- El layout no es solo estetico: el entrevistador va a navegar el arbol de directorios.
