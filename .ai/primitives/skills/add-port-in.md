---
name: add-port-in
intent: Agregar un puerto de entrada (driving port) que define el contrato del use case hacia el exterior
inputs: [port_name, use_case_interface, request_type, response_type]
preconditions:
  - application/usecase/ existe en el modulo objetivo
postconditions:
  - Interfaz del puerto en application/usecase/<aggregate>/ (o domain/usecase/ segun convencion del PoC)
  - Implementacion concreta del use case
  - Controller/Handler llama al puerto, no a la implementacion
related_rules: [clean-arch-boundaries, architecture-clean, java-version]
---

# Skill: add-port-in

## Pasos

1. **Definir interfaz** del puerto de entrada:
   ```java
   // application/usecase/risk/EvaluateTransactionUseCase.java
   public interface EvaluateTransactionUseCase {
       Future<RiskDecision> evaluate(EvaluateTransactionRequest request);
   }
   ```

2. **Implementar el use case**:
   ```java
   // application/usecase/risk/EvaluateTransactionUseCaseImpl.java
   public class EvaluateTransactionUseCaseImpl implements EvaluateTransactionUseCase {
       private final RuleEngine ruleEngine;
       private final TransactionRepository repository;
       private final EventPublisher eventPublisher;

       @Override
       public Future<RiskDecision> evaluate(EvaluateTransactionRequest request) {
           // 1. Validar idempotencia
           // 2. Cargar contexto (historial, velocidad)
           // 3. Ejecutar reglas
           // 4. Persistir decision
           // 5. Publicar evento
           // 6. Retornar decision
       }
   }
   ```

3. **Controller usa solo la interfaz**:
   ```java
   // infrastructure/controller/RiskHandler.java
   public RiskHandler(EvaluateTransactionUseCase useCase) { ... }
   ```

4. **Wiring**: en `config/` o verticle, crear `EvaluateTransactionUseCaseImpl` y pasarlo al handler.

## Notas
- El puerto de entrada define el contrato del dominio hacia el mundo exterior.
- La interfaz debe estar en `application/` o `domain/`, nunca en `infrastructure/`.
- Un use case = una operacion de negocio. No combinar operaciones.
