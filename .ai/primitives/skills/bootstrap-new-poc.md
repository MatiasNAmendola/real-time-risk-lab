---
name: bootstrap-new-poc
intent: Arrancar una nueva PoC siguiendo las convenciones del repo (layout, naming, stack)
inputs: [poc_name, poc_purpose, primary_pattern]
preconditions:
  - poc/ directory existe en el repo raiz
  - Java 25 y Maven 3.9+ instalados
postconditions:
  - poc/<poc_name>/ creado con layout canónico
  - README.md con proposito, como correr, stack
  - pom.xml con Java 25, Vert.x 5.0.12, dependencias minimas
  - .gitignore con target/, *.class, etc.
related_rules: [java-version, architecture-clean, naming-conventions, containers-docker]
---

# Skill: bootstrap-new-poc

## Pasos

1. **Crear directorio**:
   ```bash
   mkdir -p poc/<poc-name>/{src/main/java/com/naranjax/interview/<domain>,src/test/java,scripts}
   ```

2. **pom.xml minimo** con Java 25 y Vert.x:
   ```xml
   <project>
     <groupId>com.naranjax.interview</groupId>
     <artifactId>poc-<poc-name></artifactId>
     <version>1.0.0-SNAPSHOT</version>
     <properties>
       <java.version>25</java.version>
       <maven.compiler.release>25</maven.compiler.release>
       <vertx.version>5.0.12</vertx.version>
     </properties>
     <dependencies>
       <dependency>
         <groupId>io.vertx</groupId>
         <artifactId>vertx-stack-depchain</artifactId>
         <version>${vertx.version}</version>
         <type>pom</type>
         <scope>import</scope>
       </dependency>
     </dependencies>
   </project>
   ```

3. **Layout canonico** (estilo enterprise Go):
   ```
   src/main/java/com/naranjax/interview/<domain>/
   ├── domain/
   │   ├── entity/
   │   ├── repository/      # puertos de salida
   │   ├── usecase/         # puertos de entrada
   │   ├── service/         # servicios de dominio
   │   └── rule/
   ├── application/
   │   ├── usecase/<aggregate>/
   │   ├── mapper/
   │   └── dto/
   ├── infrastructure/
   │   ├── controller/
   │   ├── consumer/
   │   ├── repository/
   │   ├── resilience/
   │   └── time/
   ├── config/
   └── cmd/
   ```

4. **README.md**: proposito, como correr (`./scripts/run.sh`), stack, estado.

5. **scripts/run.sh** y **scripts/test.sh** ejecutables.

6. **Agregar al poc-inventory.md** en `.ai/context/poc-inventory.md`.

## Notas
- No crear PoCs dentro de directorios existentes (`tests/`, `cli/`). Solo bajo `poc/`.
- Cada PoC debe ser independiente (no depender de otras PoCs en runtime).
