# ADR-0004 · Migraciones versionadas con Flyway y ddl-auto=validate

- **Estado:** Aceptada
- **Fecha:** 2026-11-20 (B5)

## Contexto

`ddl-auto=update` dejaba que Hibernate modificara el esquema por su cuenta: nunca borra ni renombra columnas, no crea índices útiles y no deja historial de cambios. Para una base con datos reales de clientes eso no es aceptable.

## Decisión

- Flyway aplica `src/main/resources/db/migration/V<n>__*.sql` al arrancar.
- `V1__esquema_inicial.sql` se **generó con Hibernate** y se **revisó a mano**: se agregaron índices para las consultas de agenda, nombres legibles para las constraints y `check` de negocio.
- Hibernate corre con `ddl-auto=validate`.
- Una migración aplicada nunca se edita.

## Alternativas consideradas

- **Liquibase:** igual de válido, pero XML o YAML sobre SQL es una capa extra sin beneficio aquí.
- **Seguir con `update`:** sin control ni historial.
- **Escribir la `V1` a mano desde cero:** más propenso a errores que partir del esquema generado.

## Consecuencias

- Cada cambio de esquema es un archivo revisado en un PR.
- `validate` detecta tablas o columnas faltantes y tipos incompatibles, pero **no** longitudes, `nullable`, índices ni constraints. La disciplina de "entidad y migración en el mismo PR" sigue siendo necesaria.
- Si se agrega un valor a un enum persistido, hay que actualizar su `check`.
