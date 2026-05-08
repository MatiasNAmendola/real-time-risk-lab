---
name: add-jacoco-coverage-target
intent: Agregar o actualizar un target de cobertura JaCoCo en un modulo Gradle
inputs: [module_path, line_coverage_target, branch_coverage_target]
preconditions:
  - build.gradle.kts del modulo existe
  - Hay tests ejecutables en el modulo
postconditions:
  - JaCoCo plugin configurado en build.gradle.kts
  - ./gradlew test jacocoTestReport falla si coverage < target
  - Reporte generado en build/reports/jacoco/test/
related_rules: [java-version, testing-atdd]
---

# Skill: add-jacoco-coverage-target

## Pasos

1. **Agregar plugin en build.gradle.kts**:
   ```xml
   <plugin>
     <groupId>org.jacoco</groupId>
     <artifactId>jacoco-gradle-plugin</artifactId>
     <version>0.8.12</version>
     <executions>
       <execution>
         <id>prepare-agent</id>
         <goals><goal>prepare-agent</goal></goals>
       </execution>
       <execution>
         <id>report</id>
         <phase>verify</phase>
         <goals><goal>report</goal></goals>
       </execution>
       <execution>
         <id>check</id>
         <phase>verify</phase>
         <goals><goal>check</goal></goals>
         <configuration>
           <rules>
             <rule>
               <element>BUNDLE</element>
               <limits>
                 <limit>
                   <counter>LINE</counter>
                   <value>COVEREDRATIO</value>
                   <minimum>0.80</minimum>
                 </limit>
                 <limit>
                   <counter>BRANCH</counter>
                   <value>COVEREDRATIO</value>
                   <minimum>0.75</minimum>
                 </limit>
               </limits>
             </rule>
           </rules>
         </configuration>
       </execution>
     </executions>
   </plugin>
   ```

2. **Excluir clases generadas** si aplica:
   ```xml
   <configuration>
     <excludes>
       <exclude>**/dto/**</exclude>
       <exclude>**/cmd/**</exclude>
     </excludes>
   </configuration>
   ```

3. **Verificar**: `./gradlew :<module-path>:test :<module-path>:jacocoTestReport`. El build falla si coverage < target.

4. **Ver reporte**: abrir `<module>/build/reports/jacoco/test/index.html`.

## Notas
- JaCoCo corre sobre el baseline actual Java 21; validar compatibilidad antes de subir classfile target.
- Para PoC bare-javac sin Gradle: generar reporte manualmente con `jacoco-cli.jar`.
- El target de coverage debe ser realista: no pongas 100% si hay codigo de infraestructura dificil de testear.
