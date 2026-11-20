# Operación (runbook)

Procedimientos para desplegar, mantener y diagnosticar Sillón. Cada sección se puede seguir sin leer las demás.

## Desplegar

Sillón es una imagen Docker y corre en cualquier plataforma de contenedores (Render, AWS ECS o App Runner, Fly.io…). La plataforma de producción definitiva está **pendiente de decidir**; el despliegue del curso en Render usa otro repositorio y otra base de datos.

### Checklist del primer despliegue

1. **Base de datos:** PostgreSQL vacía y nueva. Flyway crea el esquema al arrancar.
   - Conexión directa o *session pooler* (5432), **no** *transaction pooler*.
   - `sslmode=require` en la URL.
2. **Variables de entorno:** todas las marcadas como obligatorias en [`configuracion.md`](configuracion.md#variables-de-entorno-de-producción), con `SPRING_PROFILES_ACTIVE=prod`.
3. **Red:** el contenedor **solo** debe recibir tráfico del proxy o balanceador de la plataforma, nunca directamente de internet. La IP real del cliente depende de esto ([ADR-0007](adr/0007-ip-real-con-forward-headers-native.md)).
4. **Dominio:** la API y el frontend en el mismo sitio (`api.dominio.com` y `app.dominio.com`), con HTTPS en los dos. Sin esto, la cookie de sesión no funciona en Safari.
5. **Stripe:** destino de webhook hacia `https://api.dominio.com/api/pagos/webhook` (ver [`integraciones.md`](integraciones.md#configuración-en-producción)).
6. **Verificación después del despliegue:**
   ```bash
   API=https://api.dominio.com
   curl -s -o /dev/null -w "swagger (debe ser 404): %{http_code}\n" $API/swagger-ui.html
   curl -s -w "\nlogin (debe ser 401): %{http_code}\n" -X POST $API/api/auth/login \
     -H 'Content-Type: application/json' -d '{"email":"nadie@x.co","password":"12345678"}'
   ```
   Y en los logs: `Successfully applied N migrations` o `Schema "public" is up to date`.

### Construir y probar la imagen en local

```bash
docker compose up -d
docker build -t sillon:local .
docker run --rm --name sillon-app --network sillon_default -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRETO="$(openssl rand -base64 48)" \
  -e URL_PUBLICA=http://localhost:8080 \
  -e CORS_ORIGENES=http://localhost:3000 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://sillon-postgres:5432/sillon \
  -e SPRING_DATASOURCE_USERNAME=sillon \
  -e SPRING_DATASOURCE_PASSWORD=sillon_local \
  sillon:local
```

## Rotar secretos

### `JWT_SECRETO`

1. Genera uno nuevo: `openssl rand -base64 48`.
2. Reemplázalo en la plataforma y redespliega.

**Efecto:** todos los access tokens vigentes dejan de ser válidos, porque su firma no coincide. Las sesiones **no** se cierran: el refresh token no es un JWT, así que el front obtiene un access nuevo en el siguiente refresh sin que nadie note nada.

### Cerrar todas las sesiones (incidente de seguridad)

Si sospechas que se robaron sesiones, revócalas todas y además rota `JWT_SECRETO`:

```sql
update sesiones_refresco set revocado_en = now() where revocado_en is null;
```

Todos los usuarios tendrán que volver a iniciar sesión.

### Llaves de Stripe o Gemini

Genera la nueva en el panel del proveedor, reemplázala en la plataforma, redespliega y **solo entonces** revoca la anterior.

### Si un secreto llegó a un commit

Considéralo comprometido aunque borres el commit: el historial ya pudo copiarse. **Rótalo primero** y limpia el historial después, si hace falta.

## Respaldos

- Usa los respaldos automáticos del proveedor de PostgreSQL y verifica su retención.
- Respaldo manual:
  ```bash
  pg_dump "$URL_DE_CONEXION" --format=custom --file=sillon-$(date +%F).dump
  ```
- Restaurar en una base **vacía**:
  ```bash
  pg_restore --dbname="$URL_DE_CONEXION" --no-owner sillon-AAAA-MM-DD.dump
  ```
  La tabla `flyway_schema_history` viaja en el respaldo, así que Flyway reconoce las migraciones ya aplicadas.

## Qué hago si…

### La app no arranca

Busca en el log la primera línea con `Caused by`:

| Mensaje | Causa | Solución |
|---|---|---|
| `Falta JWT_SECRETO` | No hay secreto, está vacío o llegó como `${…}` | Define `JWT_SECRETO`, o `sillon.jwt.secreto` en `secrets.properties` |
| `debe tener al menos 32 caracteres` | Secreto corto | `openssl rand -base64 48` |
| `Falta CORS_ORIGENES` / `Origen CORS inválido` | Variable faltante o con `*`, barra final o `http://` | Origen exacto: `https://app.dominio.com` |
| `Configuración de sesión inválida` | Falta una propiedad `sillon.sesion.*` | Revisa `application.properties` |
| `Could not resolve placeholder` | Falta una variable obligatoria del perfil `prod` | Revisa la tabla de variables de [`configuracion.md`](configuracion.md) |
| `Schema-validation: missing column` / `wrong column type` | Una entidad cambió sin su migración | Agrega la migración `V<n>` |
| `Validate failed: Migration checksum mismatch` | Se editó una migración ya aplicada | Revierte el archivo; el cambio va en una migración nueva |
| `Found non-empty schema(s) … without schema history table` | Base con tablas que Flyway no creó | En local: `docker compose down -v`. En producción: usar una base nueva |
| `Connection refused` a `localhost:5432` | PostgreSQL apagado | `docker compose up -d` |
| `password authentication failed` en local | `secrets.properties` todavía tiene una contraseña de base de datos vieja | Borra las líneas `spring.datasource.*` de `secrets.properties` |

### Muchos 429

- Mira la regla en `ReglaLimite` y el `Retry-After` de la respuesta.
- **Si todos los clientes comparten la misma IP** en los logs, probablemente el proxy no está pasando `X-Forwarded-For`, o la IP del proxy no es de red privada, y entonces Tomcat no confía en él. Revisa la red de la plataforma. **No** cambies `forward-headers-strategy` a `framework`: reabre la falsificación de IP.
- Los contadores viven en memoria: reiniciar la app los borra. Úsalo solo como alivio temporal.

### Los usuarios pierden la sesión

- **Se pierde tras recargar la página:** la cookie no llega. Revisa que el front y la API compartan sitio, que todo sea HTTPS (`cookie-segura=true` exige HTTPS) y que el front llame a `/api/auth/*` con `credentials: 'include'`.
- **Se pierde al tener varias pestañas abiertas:** si pasa a menudo, sube `sillon.sesion.segundos-gracia`. El front también debe evitar varios refrescos en paralelo (un solo refresh en curso).
- En los logs, `Reuso de refresh token: se revoca la familia …` indica un reuso **fuera** de la ventana de gracia: un posible robo o un front que guarda cookies viejas.

### Los webhooks de Stripe fallan

| Síntoma | Causa probable |
|---|---|
| 400 en todos | `STRIPE_SECRETO_WEBHOOK` equivocado. El de `stripe listen` no sirve en producción |
| 400 intermitentes | El reloj del servidor está desfasado más de 300 s |
| 200 pero el plan no cambia | El evento no trae `payment_status = paid`, o falta la metadata `salonId`/`tipo` |
| `procesado: false` | Es un evento repetido: es lo esperado |

### La IA responde 503

Ningún proveedor respondió. Busca en el log `IA falló`:

- `ERROR … por configuración`: llave, URL o modelo incorrectos.
- `WARN … temporalmente`: el proveedor está caído o saturado (429 o 5xx). Se recupera solo.

## Mantenimiento

| Tarea | Frecuencia | Cómo |
|---|---|---|
| PRs de Dependabot | Semanal (lunes) | Revisar el changelog de la dependencia y hacer merge si el CI sale en verde. Las versiones mayores van con más calma |
| Limpieza de sesiones | Automática, diaria (3:30 a. m.) | `SesionService.limpiarVencidas` |
| Revisar los logs de `Reuso de refresh token` | Semanal | Muchos casos indican robo o un bug del front |
