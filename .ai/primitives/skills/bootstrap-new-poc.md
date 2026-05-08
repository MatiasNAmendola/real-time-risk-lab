---
name: bootstrap-new-poc
intent: Arrancar una nueva PoC siguiendo las convenciones del repo (layout, naming, stack)
inputs: [poc_name, poc_purpose, primary_pattern]
preconditions:
  - poc/ directory existe en el repo raiz
  - Java 21+ y Gradle wrapper disponibles
postconditions:
  - poc/<poc_name>/ creado con layout canónico
  - README.md con proposito, como correr, stack
  - build.gradle.kts con Java 21 baseline, Vert.x 5.0.12, dependencias minimas
  - .gitignore con build/, *.class, etc.
related_rules: [java-version, architecture-clean, naming-conventions, containers-docker]
---

# Skill: bootstrap-new-poc

## Pasos

1. **Crear directorio**:
   ```bash
   mkdir -p poc/<poc-name>/{src/main/java/io/riskplatform/practice/<domain>,src/test/java,scripts}
   ```

2. **build.gradle.kts minimo** con Java 21 baseline y Vert.x:
   ```xml
   <project>
     <groupId>io.riskplatform.practice</groupId>
     <artifactId>poc-<poc-name></artifactId>
     <version>1.0.0-SNAPSHOT</version>
     <properties>
       <java.version>25</java.version>
       <gradle.compiler.release>21</gradle.compiler.release>
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
   src/main/java/io/riskplatform/practice/<domain>/
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
