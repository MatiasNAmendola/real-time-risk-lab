# Regla — versión Java

## Estado canónico operativo

- El baseline ejecutable actual del repo es **Java 21 LTS**.
- Todo código Java debe compilar con `--release 21` mediante Gradle toolchains/convention plugins.
- Java 25 LTS queda como **objetivo arquitectónico documentado**, no como requisito actual de build.

## Por qué no `--release 25` todavía

El repo usa Gradle, Shadow/JMH/Karate/ArchUnit y tooling que todavía puede fallar con classfile 25. El compromiso defendible para la demo es:

> “El baseline ejecutable está en Java 21 LTS por compatibilidad de tooling, y dejé documentada la discusión de migrar a Java 25 LTS para aprovechar runtime moderno.”

## Regla práctica

- No cambiar el build a Java 25 sin validar todo el pipeline.
- No afirmar en README/STATUS que el build real usa Java 25.
- Si se ejecuta con JDK 25, debe seguir generando bytecode `--release 21` hasta que el ADR de compatibilidad se actualice.

## Verificación

```bash
./gradlew clean build -x test
./nx test --composite quick
```

Ver también: `docs/26-java-version-compat-2026.md`.
