---
name: add-port-out
intent: Agregar un nuevo puerto de salida (driven port) con su adapter de infraestructura
inputs: [port_name, domain_package, adapter_type, adapter_impl]
preconditions:
  - domain/ y infrastructure/ existen en el modulo objetivo
postconditions:
  - Interfaz del puerto en domain/repository/ (o domain/service/ si es servicio externo)
  - Adapter concreto en infrastructure/<adapter_type>/
  - Wiring en config/ o verticle principal
  - Unit test del adapter con mock del sistema externo
related_rules: [clean-arch-boundaries, architecture-clean, java-version, naming-conventions]
---

# Skill: add-port-out

## Pasos

1. **Definir la interfaz** en `domain/repository/<PortName>.java`:
   ```java
   public interface TransactionRepository {
       Future<Transaction> findById(String id);
       Future<Void> save(Transaction transaction);
   }
   ```
   - Solo tipos de dominio en la firma. Jamas `JsonObject`, `ResultSet`, etc.

2. **Implementar el adapter** en `infrastructure/repository/<PortName>Postgres.java` (o el tipo que aplique):
   ```java
   public class TransactionRepositoryPostgres implements TransactionRepository {
       private final Pool pool;

       @Override
       public Future<Transaction> findById(String id) {
           return pool.preparedQuery("SELECT * FROM transactions WHERE id = $1")
               .execute(Tuple.of(id))
               .map(rows -> mapper.toTransaction(rows.iterator().next()));
       }
   }
   ```

3. **Wiring en config/** o verticle:
   - Crear instancia del adapter.
   - Pasarla al use case por constructor (no DI framework en PoCs bare-javac).

4. **Test unitario** con Mockito o implementacion fake:
   ```java
   class TransactionRepositoryPostgresTest {
       // Test con pool mockeado o con Testcontainers Postgres
   }
   ```

5. **Chequear boundaries**: nada de `infrastructure/` debe importarse desde `domain/`.

## Notas
- El puerto vive en `domain/`. El adapter en `infrastructure/`. Esta separacion es la que valida el entrevistador.
- Nunca usar `static` para acceder a adapters. Siempre inyeccion por constructor.
- Para Valkey/Redis: puerto en `domain/repository/CacheRepository.java`, adapter en `infrastructure/repository/CacheRepositoryValkey.java`.
