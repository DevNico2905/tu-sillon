# Architecture Decision Records (ADR)

Un ADR registra **una** decisión de arquitectura: el contexto, qué se decidió, qué alternativas se descartaron y qué se aceptó a cambio. El código dice *qué* hace el sistema; los ADR dicen ***por qué***.

## Índice

| ADR | Decisión | Estado |
|---|---|---|
| [0001](0001-monolito-modular-por-dominio.md) | Monolito modular organizado por dominio | Aceptada |
| [0002](0002-multi-inquilino-por-columna.md) | Multi-inquilino con columna salon_id y 404 ante datos ajenos | Aceptada |
| [0003](0003-postgresql-en-todos-los-entornos.md) | PostgreSQL en todos los entornos | Aceptada |
| [0004](0004-migraciones-con-flyway-y-validate.md) | Migraciones versionadas con Flyway y ddl-auto=validate | Aceptada |
| [0005](0005-jwt-corto-y-refresh-token-en-cookie.md) | Access token corto y refresh token en cookie httpOnly con rotación | Aceptada |
| [0006](0006-rate-limit-en-memoria-con-bucket4j.md) | Rate limit en memoria con Bucket4j | Aceptada |
| [0007](0007-ip-real-con-forward-headers-native.md) | IP real del cliente con forward-headers-strategy=native | Aceptada |
| [0008](0008-configuracion-segura-por-defecto.md) | Configuración segura por defecto y perfil prod | Aceptada |
| [0009](0009-webhooks-stripe-firma-y-jackson.md) | Webhooks de Stripe: firma con stripe-java, lectura con Jackson | Aceptada |
| [0010](0010-ia-por-http-con-fallback-y-creditos-atomicos.md) | IA por HTTP, proveedores en cadena y créditos atómicos | Aceptada |
| [0011](0011-github-flow-con-ci-obligatorio.md) | GitHub Flow con CI obligatorio | Aceptada |
| [0012](0012-contenedor-sin-root.md) | Contenedor sin root | Aceptada |

## Cómo escribir uno nuevo

1. Copia la plantilla de abajo en `NNNN-titulo-en-minusculas.md`, con el número siguiente.
2. Agrégalo a este índice.
3. Va en el **mismo PR** que implementa la decisión.
4. Un ADR aceptado **no se edita**: si la decisión cambia, se escribe uno nuevo y el anterior pasa a "Reemplazada por ADR-NNNN".

```markdown
# ADR-NNNN · Título

- **Estado:** Propuesta | Aceptada | Reemplazada por ADR-NNNN
- **Fecha:** AAAA-MM-DD

## Contexto
¿Qué problema o fuerza obliga a decidir?

## Decisión
¿Qué se decidió, en concreto?

## Alternativas consideradas
¿Qué más se evaluó y por qué se descartó?

## Consecuencias
¿Qué se gana, qué se acepta a cambio y qué hay que vigilar?
```
