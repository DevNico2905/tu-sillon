# ADR-0005 · Access token corto y refresh token en cookie httpOnly con rotación

- **Estado:** Aceptada
- **Fecha:** 2026-11-19 (B3)

## Contexto

El JWT duraba 8 horas, sin forma de renovarlo ni de revocarlo. Un token robado servía todo ese tiempo y cerrar sesión no lo invalidaba. Además se viene un frontend web, donde el almacenamiento del token importa (XSS).

## Decisión

- **Access token:** JWT de 15 minutos, solo en memoria del front.
- **Refresh token:** 256 bits aleatorios (no es un JWT) en la cookie `sillon_refresh` con `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`. Se guarda como hash SHA-256.
- **Rotación** en cada uso, agrupada en *familias*. Reusar un token ya usado revoca la familia, salvo dentro de una **ventana de gracia de 10 s** para refrescos simultáneos.
- Vence tras 7 días sin uso, con un máximo absoluto de 30 días. Hay logout por dispositivo.
- Front y API en el mismo *sitio* (`app.` y `api.` del mismo dominio).

## Alternativas consideradas

- **JWT largo en `localStorage`:** se lo roba cualquier XSS y no se puede revocar.
- **Refresh token en el cuerpo de la respuesta:** más simple, pero queda expuesto al JavaScript.
- **Sesiones de servidor (cookie de sesión clásica):** obliga a mantener estado por usuario en cada petición y a tratar CSRF en toda la API.
- **SHA-256 frente a BCrypt para el hash:** BCrypt protege secretos de baja entropía (contraseñas) y no permite buscar por hash. Un valor de 256 bits aleatorios no se puede adivinar por fuerza bruta.

## Consecuencias

- Robar el refresh token sirve poco: la rotación lo detecta.
- Después del logout, el access token sigue válido hasta 15 minutos (se acepta).
- `SesionService.rotar` usa `noRollbackFor` para que la revocación sobreviva al 401.
- El front tiene que manejar el refresh: un solo refresh en curso y reintentar la petición tras un 401.
- Exige que front y API compartan sitio.
