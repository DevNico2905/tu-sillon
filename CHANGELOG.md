# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Cada entrada enlaza su ADR cuando hubo una decisión de arquitectura.

## [Sin publicar]

### Corregido
- **B11 · `Retry-After` visible para el front:** CORS expone la cabecera, que el navegador ocultaba en las respuestas a otro origen. Sin ella, el front no podía mostrar la cuenta regresiva de un 429.

### Agregado
- **B13 · Editar los datos del salón (R44):** `PUT /api/salon` cambia nombre, ciudad, dirección y teléfono. El slug se conserva para no romper los enlaces públicos.
- **B12 · Editar y desactivar estilistas (R43):** `PUT /api/estilistas/{id}` cambia nombre, servicios, estado y, si viene, la contraseña. No se puede desactivar a quien tiene citas pendientes o confirmadas por terminar (409). Desactivar o cambiar la contraseña cierra todas sus sesiones. La respuesta de estilistas agrega `servicioIds`.
- **B7 · Cualquier estilista (R40):** `estilistaId` opcional en la disponibilidad y al agendar. Sin él, la disponibilidad une las horas de todos los estilistas que ofrecen el servicio y la reserva asigna al libre con menos citas ese día.
- **B9 · Horario en el catálogo público (R42):** `GET /api/publico/salones/{slug}` agrega `horaApertura`, `horaCierre` y `abreDomingos`, para que la web bloquee días y horas cerradas sin interpretar el texto.

### Cambiado
- **B12 · Cupo del plan GRATIS (R9):** cuenta solo estilistas activos, así que desactivar a uno libera su cupo.
- **B8 · Tono de tú (R41):** mensajes de error, de pago y el mensaje seguro del chatbot pasan a tú; los prompts del chatbot y del recordatorio piden tutear.

### Documentación
- Documentación completa del proyecto: `docs/` (arquitectura, seguridad, base de datos, configuración, integraciones, operación), ADRs, `CONTRIBUTING.md`, y `SPEC.md` y `AGENTS.md` versionados.

## [1.1.0] · 2026-11-20 · Endurecimiento para producción

Tanda B0–B6, basada en una revisión de seguridad completa del backend (ver [`docs/seguridad.md`](docs/seguridad.md#revisión-de-seguridad-noviembre-2026)).

### Agregado
- **B0 · CI:** GitHub Actions corre `./mvnw verify` en cada PR y en `main`; Dependabot semanal para Maven, GitHub Actions y Docker; plantilla de PR. `main` protegida con un ruleset ([ADR-0011](docs/adr/0011-github-flow-con-ci-obligatorio.md)).
- **B2 · CORS:** orígenes permitidos por la variable `CORS_ORIGENES`, validados al arrancar (sin comodines, solo `https://` o `http://localhost`).
- **B3 · Sesiones:** refresh token aleatorio en cookie `httpOnly` (`SameSite=Strict`, `Path=/api/auth`), guardado como hash SHA-256, con rotación, detección de reuso y ventana de gracia de 10 s. Endpoints `POST /api/auth/refresh` y `POST /api/auth/logout`. Limpieza diaria de sesiones vencidas ([ADR-0005](docs/adr/0005-jwt-corto-y-refresh-token-en-cookie.md)).
- **B4 · Rate limit:** Bucket4j en memoria por IP, por correo (login) y por salón (chat y reservas). Responde 429 con `Retry-After` ([ADR-0006](docs/adr/0006-rate-limit-en-memoria-con-bucket4j.md)).
- **B5 · Migraciones:** Flyway con `V1__esquema_inicial.sql` (índices para las consultas de agenda, constraints con nombre y checks de negocio). `compose.yaml` con PostgreSQL 17 para desarrollo ([ADR-0003](docs/adr/0003-postgresql-en-todos-los-entornos.md), [ADR-0004](docs/adr/0004-migraciones-con-flyway-y-validate.md)).

### Cambiado
- **B1 · Configuración segura por defecto:** `JWT_SECRETO` es obligatorio en todos los entornos y se rechazan placeholders sin resolver. Nuevo perfil `prod`: Swagger apagado, sin valores por defecto para la base de datos ni la URL pública, y errores sin detalles internos ([ADR-0008](docs/adr/0008-configuracion-segura-por-defecto.md)).
- **B3:** el access token pasa de 480 a **15 minutos**. El login deja de ser transacción de solo lectura.
- **B4:** `server.forward-headers-strategy` pasa de `framework` a `native`, porque `framework` dejaba falsificar la IP del cliente ([ADR-0007](docs/adr/0007-ip-real-con-forward-headers-native.md)).
- **B5:** MySQL reemplazado por PostgreSQL en local y en CI. `ddl-auto` pasa de `update` a `validate`.
- **B6 · Contenedor:** corre con el usuario sin privilegios `10001`, el `.jar` es de solo lectura y la JVM termina ante un `OutOfMemoryError` ([ADR-0012](docs/adr/0012-contenedor-sin-root.md)).

### Seguridad
- Corregido: secreto JWT con valor por defecto público en el repositorio (crítico).
- Corregido: el chat público permitía agotar los créditos de IA de cualquier salón.
- Corregido: login y registro sin límite de intentos.
- Corregido: IP del cliente falsificable mediante `X-Forwarded-For`.

## [1.0.0] · 2026-11-19 · Entrega del curso

Proyecto final de los módulos 4 y 5 de *Java AI Engineer* (Dev Senior Code), calificado con 5.0/5.0.

### Agregado
- Registro de salones y login con JWT; roles DUENO, ESTILISTA y ADMIN; aislamiento multi-inquilino.
- Catálogo de servicios, estilistas, clientes y agenda con reglas de horario, cruces y transiciones de estado.
- Reserva pública y disponibilidad calculada en Java.
- Recordatorios y chatbot con IA (Gemini con respaldo), créditos atómicos y filtro anti-promesas.
- Pagos con Stripe: plan PRO, paquete de créditos y webhook firmado e idempotente.
- Swagger, colección de Postman con pruebas y despliegue en Render con Supabase.
