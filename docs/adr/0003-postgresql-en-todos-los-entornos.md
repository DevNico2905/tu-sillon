# ADR-0003 · PostgreSQL en todos los entornos

- **Estado:** Aceptada
- **Fecha:** 2026-11-20 (B5)

## Contexto

Se desarrollaba con MySQL en local y en el CI, y se desplegaba sobre PostgreSQL. Con `ddl-auto=update` no se notaba la diferencia, pero las migraciones con Flyway son SQL escrito a mano y dependen del motor.

## Decisión

PostgreSQL 17 en todos lados: `compose.yaml` en local (solo accesible en `127.0.0.1`), un servicio en GitHub Actions y PostgreSQL gestionado en producción. Se retiró el driver de MySQL.

## Alternativas consideradas

- **Dos juegos de migraciones**, uno por motor: duplicación y riesgo de que diverjan.
- **SQL portable** entre los dos: limita los tipos (`timestamptz`, `check`…) y siempre se cuela algo propio de un motor.
- **H2 en los tests:** no es PostgreSQL. Los errores aparecerían recién en producción.

## Consecuencias

- Lo que pasa en local es lo que pasa en producción.
- Se requiere Docker para desarrollar y para correr los tests.
- Se puede usar todo lo propio de PostgreSQL sin miedo.
