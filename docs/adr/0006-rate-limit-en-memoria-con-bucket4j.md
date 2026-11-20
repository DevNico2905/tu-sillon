# ADR-0006 · Rate limit en memoria con Bucket4j

- **Estado:** Aceptada
- **Fecha:** 2026-11-19 (B4)

## Contexto

Login, registro, reservas y sobre todo el chat público (que gasta créditos de IA del salón) no tenían límite de peticiones.

## Decisión

- Token bucket con **Bucket4j**, baldes en una caché **Caffeine** acotada (100.000 entradas, 1 hora sin uso).
- Claves por IP, por correo (en el login, antes de BCrypt) y **por salón** en el chat y las reservas, para que rotar IPs no sirva.
- Todas las reglas en el enum `ReglaLimite`. Respuesta 429 con `Retry-After`.

## Alternativas consideradas

- **Redis con Bucket4j:** necesario con varias instancias. Hoy es infraestructura extra sin necesidad.
- **Límite en el proxy o WAF de la plataforma:** depende del proveedor, que aún no está decidido, y no conoce el negocio (correo, salón).
- **Implementación propia de ventanas fijas:** reinventar algo ya probado, con peores bordes.

## Consecuencias

- Sin infraestructura nueva y con latencia nula.
- Cada instancia cuenta por separado y un reinicio borra los contadores. **Al escalar a varias instancias hay que migrar a Redis**; las reglas no cambian.
- El límite por correo permite bloquear durante unos minutos el login de una cuenta ajena (se acepta).
