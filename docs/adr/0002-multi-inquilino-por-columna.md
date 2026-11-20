# ADR-0002 · Multi-inquilino con columna salon_id y 404 ante datos ajenos

- **Estado:** Aceptada
- **Fecha:** 2026-11-18 (entrega del curso)

## Contexto

Muchos salones comparten la aplicación. Ningún salón puede ver ni modificar datos de otro, y el costo por salón debe ser mínimo.

## Decisión

- **Una sola base de datos y un solo esquema**, con `salon_id` en cada tabla de negocio.
- El `salonId` sale **siempre** del usuario autenticado (`UsuarioPrincipal`), nunca de la URL ni del cuerpo.
- Toda búsqueda por id filtra también por salón (`findByIdAndSalonId`). Si no aparece, responde **404**, aunque el id exista en otro salón.

## Alternativas consideradas

- **Base de datos o esquema por salón:** aislamiento más fuerte, pero un costo y una operación (migraciones por inquilino) desproporcionados.
- **Row Level Security de PostgreSQL:** buen complemento a futuro, aunque necesita pasar el inquilino a la sesión de base de datos en cada transacción.
- **Responder 403 ante datos ajenos:** confirmaría que el id existe.

## Consecuencias

- Es simple y barato. El aislamiento depende de la disciplina en cada consulta, y por eso es una regla obligatoria de `AGENTS.md` y del checklist de PR.
- Un olvido en una consulta es una fuga de datos entre salones. RLS queda como defensa adicional posible.
