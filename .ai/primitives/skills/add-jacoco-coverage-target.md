---
name: add-jacoco-coverage-target
intent: Agregar o actualizar un target de cobertura JaCoCo en un modulo Maven
inputs: [module_path, line_coverage_target, branch_coverage_target]
preconditions:
  - pom.xml del modulo existe
  - Hay tests ejecutables en el modulo
postconditions:
  - JaCoCo plugin configurado en pom.xml
  - mvn verify falla si coverage < target
  - Reporte generado en target/site/jacoco/
related_rules: [java-version, testing-atdd]
---

# Skill: add-jacoco-coverage-target

## Pasos

1. **Agregar plugin en pom.xml**:
   ```xml
   <plugin>
     <groupId>org.jacoco</groupId>
     <artifactId>jacoco-maven-plugin</artifactId>
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

3. **Verificar**: `mvn verify -pl <module>`. El build falla si coverage < target.

4. **Ver reporte**: abrir `<module>/target/site/jacoco/index.html`.

## Notas
- JaCoCo 0.8+ soporta Java 25.
- Para PoC bare-javac sin Maven: generar reporte manualmente con `jacoco-cli.jar`.
- El target de coverage debe ser realista: no pongas 100% si hay codigo de infraestructura dificil de testear.
