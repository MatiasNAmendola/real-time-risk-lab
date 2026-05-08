---
name: add-value-object
intent: Agregar un Value Object de dominio inmutable con validacion y semantica explicita
inputs: [vo_name, fields, validation_rules]
preconditions:
  - domain/entity/ o domain/vo/ existe
postconditions:
  - Clase inmutable sin identidad propia
  - Comparacion por valor (equals/hashCode)
  - Validacion en constructor
  - Unit test cubre validacion y comparacion
related_rules: [architecture-clean, java-version, naming-conventions]
---

# Skill: add-value-object

## Pasos

1. **Crear VO** en `domain/entity/<VoName>.java` (o `domain/vo/`):
   ```java
   public record Money(long amountCents, String currency) {
       public Money {
           if (amountCents < 0) throw new IllegalArgumentException("amount cannot be negative");
           if (currency == null || currency.length() != 3) throw new IllegalArgumentException("invalid currency code");
       }

       public Money add(Money other) {
           if (!this.currency.equals(other.currency)) throw new IllegalArgumentException("currency mismatch");
           return new Money(this.amountCents + other.amountCents, this.currency);
       }

       public static Money ofARS(long amountCents) {
           return new Money(amountCents, "ARS");
       }
   }
   ```
   - Java records dan `equals`, `hashCode`, `toString` automaticamente.
   - Para Java < 16 o clases no-record: implementar manualmente.

2. **Unit test**:
   - Creacion valida.
   - Creacion invalida -> excepcion.
   - Igualdad por valor: `new Money(100, "ARS").equals(new Money(100, "ARS"))` = true.
   - Suma, operaciones.

## Notas
- VOs no tienen ID. Dos VOs con los mismos valores son iguales.
- Preferir `record` (disponible desde Java 16; baseline actual Java 21) por inmutabilidad y sintaxis concisa.
- No incluir logica que consulte infraestructura.
