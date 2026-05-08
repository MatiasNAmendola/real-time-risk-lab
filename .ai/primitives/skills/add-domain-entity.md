---
name: add-domain-entity
intent: Agregar una entidad de dominio con identidad, invariantes y comportamiento
inputs: [entity_name, identity_field, value_fields, invariants]
preconditions:
  - domain/entity/ existe
postconditions:
  - Clase de entidad con identidad explicita
  - Invariantes validadas en constructor o factory method
  - Sin dependencias de infraestructura
  - Unit test cubre invariantes y comportamiento
related_rules: [architecture-clean, clean-arch-boundaries, java-version, naming-conventions]
---

# Skill: add-domain-entity

## Pasos

1. **Crear entidad** en `domain/entity/<EntityName>.java`:
   ```java
   public final class Transaction {
       private final String id;           // identidad
       private final long amountARS;
       private final String merchantId;
       private final Instant createdAt;
       private TransactionStatus status;

       private Transaction(String id, long amountARS, String merchantId, Instant createdAt) {
           if (amountARS <= 0) throw new IllegalArgumentException("amount must be positive");
           if (merchantId == null || merchantId.isBlank()) throw new IllegalArgumentException("merchantId required");
           this.id = Objects.requireNonNull(id);
           this.amountARS = amountARS;
           this.merchantId = merchantId;
           this.createdAt = createdAt;
           this.status = TransactionStatus.PENDING;
       }

       public static Transaction create(String id, long amountARS, String merchantId) {
           return new Transaction(id, amountARS, merchantId, Instant.now());
       }

       // comportamiento de dominio
       public void approve() {
           if (this.status != TransactionStatus.PENDING) throw new IllegalStateException("...");
           this.status = TransactionStatus.APPROVED;
       }
   }
   ```

2. **Value Objects** para campos con semantica propia (ver skill add-value-object).

3. **Unit test**:
   - Constructor con valores validos.
   - Constructor con valores invalidos -> excepcion.
   - Transiciones de estado validas e invalidas.

## Notas
- Entidades tienen identidad (comparadas por ID, no por valor).
- No usar Lombok en PoCs bare-javac (no esta en classpath). En Gradle Vert.x, evaluar.
- Nunca importar nada de `infrastructure/` o `application/` desde `domain/`.
