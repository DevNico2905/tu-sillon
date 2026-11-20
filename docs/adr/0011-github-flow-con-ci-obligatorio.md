# ADR-0011 · GitHub Flow con CI obligatorio

- **Estado:** Aceptada
- **Fecha:** 2026-11-19 (B0)

## Contexto

Se empieza a trabajar con flujo profesional, con el objetivo de que `main` siempre sea desplegable.

## Decisión

- **GitHub Flow:** ramas cortas desde `main`, PR y *squash and merge*.
- Ruleset `proteger-main`: PR obligatorio (0 aprobaciones, porque hay una sola persona), check `tests` obligatorio, sin force push ni borrado.
- GitHub Actions corre `./mvnw verify` contra PostgreSQL. Dependabot semanal para Maven, Actions y Docker.
- Commits con Conventional Commits en español.

## Alternativas consideradas

- **Git Flow (`develop`, `release/*`):** más ceremonia de la que necesita un proyecto con despliegue continuo.
- **Trabajar directo en `main`:** sin red de seguridad.

## Consecuencias

- Nada roto llega a `main`.
- Al sumar más personas, basta con subir las aprobaciones requeridas a 1.
