# Configuración

## Cómo se arma la configuración

Spring Boot combina varias fuentes. Si dos definen la misma propiedad, gana la de más arriba en esta lista:

1. **Variables de entorno** (producción, CI, `docker run -e`).
2. **`secrets.properties`** en la raíz del proyecto: solo local, ignorado por Git. Se importa con `spring.config.import=optional:file:./secrets.properties`.
3. **`application-prod.properties`**, si el perfil `prod` está activo.
4. **`application.properties`**: la base.

> Ojo con el punto 2: si `secrets.properties` define algo (por ejemplo una contraseña vieja de base de datos), **le gana** a `application.properties`. Ante un comportamiento raro en local, revísalo primero.

Spring traduce los nombres de las variables de entorno: `SPRING_DATASOURCE_URL` corresponde a `spring.datasource.url`. Además, varias propiedades de Sillón leen una variable propia más corta (`JWT_SECRETO`, `CORS_ORIGENES`…), como muestran las tablas.

## Perfiles

| Perfil | Cómo se activa | Qué cambia |
|---|---|---|
| *(ninguno)* | Por defecto | Desarrollo y tests: PostgreSQL de `compose.yaml`, Swagger encendido, cookie sin `Secure`, CORS para `http://localhost:3000` |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | Exige base de datos, URL pública y CORS **sin valores por defecto**; apaga Swagger; oculta detalles de error; cookie `Secure`; IP real desde el proxy (`native`) |

La base es **segura por defecto** ([ADR-0008](adr/0008-configuracion-segura-por-defecto.md)): si se te olvida activar `prod`, lo peor que pasa es que Swagger queda visible. Nunca queda un secreto conocido.

## Variables de entorno de producción

| Variable | Obligatoria | Para qué |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ✅ | `prod` |
| `SPRING_DATASOURCE_URL` | ✅ | JDBC de PostgreSQL. Conexión directa o session pooler (puerto 5432), con `sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | ✅ | Usuario de la base |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | Contraseña de la base |
| `JWT_SECRETO` | ✅ | Secreto de firma de los JWT. 32 caracteres o más. Genéralo con `openssl rand -base64 48` |
| `URL_PUBLICA` | ✅ | URL pública de la API, para volver de Stripe Checkout |
| `CORS_ORIGENES` | ✅ | Origen(es) del frontend, separados por coma: `https://app.midominio.com` |
| `STRIPE_CLAVE_SECRETA` | Para pagos | `sk_test_…` o `sk_live_…` |
| `STRIPE_PRECIO_PRO` | Para pagos | Id del precio de la suscripción PRO (`price_…`) |
| `STRIPE_PRECIO_CREDITOS` | Para pagos | Id del precio del paquete de créditos |
| `STRIPE_SECRETO_WEBHOOK` | Para pagos | `whsec_…` del destino de webhook de producción |
| `GEMINI_LLAVE` | Para IA | Llave de Gemini (proveedor principal) |
| `CLAUDE_LLAVE` | Opcional | Llave del proveedor de respaldo |
| `PORT` | Según la plataforma | Puerto HTTP. Por defecto 8080 |

**Sin pagos o sin IA, la app arranca igual:** esas funciones responden 502 o 503 hasta que se configuren. Lo que **impide arrancar** es que falte cualquier variable marcada ✅.

## Desarrollo local: `secrets.properties`

Copia `secrets.example.properties` y llena lo que vayas a usar:

| Propiedad | Necesaria para |
|---|---|
| `sillon.jwt.secreto` | **Siempre**: sin ella la app no arranca |
| `sillon.ia.openai.llave` | Recordatorios y chatbot con Gemini real |
| `sillon.stripe.clave-secreta`, `sillon.stripe.precio-pro`, `sillon.stripe.precio-creditos` | Crear sesiones de pago |
| `sillon.stripe.secreto-webhook` | Recibir webhooks: es el `whsec_` que imprime `stripe listen` |

La base de datos local **no** va en `secrets.properties`: la define `application.properties` y la crea `compose.yaml`.

## Propiedades de la aplicación

### Negocio (`sillon.negocio.*`)

| Propiedad | Valor | Significado |
|---|---|---|
| `creditos-registro` | 10 | Créditos de IA al registrarse |
| `creditos-pro` | 300 | Créditos al activar PRO |
| `creditos-paquete` | 200 | Créditos por paquete |
| `limite-estilistas-gratis` | 2 | Estilistas máximos en el plan GRATIS |
| `sillon.zona-horaria` | `America/Bogota` | Zona del bean `Clock` |

### Seguridad

| Propiedad | Valor | Significado |
|---|---|---|
| `sillon.jwt.secreto` | `${JWT_SECRETO}` | Obligatorio. Se valida al arrancar |
| `sillon.jwt.expiracion-minutos` | 15 | Vida del access token |
| `sillon.sesion.dias-inactividad` | 7 | El refresh token vence tras estos días sin uso |
| `sillon.sesion.dias-maximos` | 30 | Máximo absoluto de una sesión |
| `sillon.sesion.segundos-gracia` | 10 | Ventana para refrescos simultáneos sin revocar |
| `sillon.sesion.cookie-segura` | `false` (base) · `true` (prod) | Atributo `Secure` de la cookie |
| `sillon.cors.origenes-permitidos` | `${CORS_ORIGENES:http://localhost:3000}` (base) · `${CORS_ORIGENES}` (prod) | Lista blanca de orígenes |

Los límites de peticiones **no** son propiedades: viven en el enum `ReglaLimite` para que cambiarlos pase por un PR revisado.

### Base de datos

| Propiedad | Valor | Significado |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate no toca el esquema; solo verifica |
| `spring.jpa.open-in-view` | `false` | Las entidades se convierten a DTO dentro del servicio |
| `spring.datasource.hikari.maximum-pool-size` | 5 | Conexiones máximas |

### IA (`sillon.ia.*`)

| Propiedad | Valor |
|---|---|
| `timeout-segundos` | 20 |
| `openai.url` | `https://generativelanguage.googleapis.com/v1beta/openai` (Gemini con formato OpenAI) |
| `openai.modelo` | `gemini-3.5-flash-lite` |
| `openai.precio-entrada-millon` / `precio-salida-millon` | 0.30 / 2.50 USD por millón de tokens, para la factura estimada |
| `claude.url` · `claude.modelo` | `https://api.anthropic.com` · `claude-haiku-4-5` |

### Stripe (`sillon.stripe.*`)

| Propiedad | Variable |
|---|---|
| `url-api` | `STRIPE_URL_API` (por defecto la API real). Existe para apuntar a `stripe-mock` en pruebas |
| `clave-secreta`, `secreto-webhook`, `precio-pro`, `precio-creditos` | Ver la tabla de producción |

### Perfil `prod` (`application-prod.properties`)

| Propiedad | Valor | Por qué |
|---|---|---|
| `server.forward-headers-strategy` | `native` | IP real desde el proxy, no falsificable ([ADR-0007](adr/0007-ip-real-con-forward-headers-native.md)) |
| `springdoc.api-docs.enabled`, `springdoc.swagger-ui.enabled` | `false` | No publicar el mapa de la API |
| `server.error.include-*` | `never` | Ningún detalle interno en los errores |

## Agregar una propiedad nueva

1. Si es obligatoria, **sin valor por defecto**, y valídala en el constructor del `record` de `@ConfigurationProperties`: rechaza `null`, vacío y `${…}`. Ver `CorsProperties`.
2. Si es un secreto, solo `${VARIABLE}`, y agrégala a `secrets.example.properties` con un valor de relleno que **no** parezca una llave real (`sk_test_TU_LLAVE`, no `sk_test_xxxxxxxx…`).
3. Documéntala en este archivo, en el mismo PR.
