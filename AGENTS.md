# Sillón · instrucciones para asistentes de código

Este archivo lo leen Claude Code y asistentes similares (`CLAUDE.md` lo importa). También sirve como resumen de convenciones para personas.

## Contexto

Backend SaaS multi-inquilino de agenda para barberías y salones de belleza.
Antes de cambiar una regla de negocio, lee `SPEC.md`: es la fuente de verdad.
Las decisiones de arquitectura y sus razones están en `docs/adr/`. No reviertas una decisión registrada sin proponer un ADR nuevo.

## Stack

- Java 17, Spring Boot 4.1, Maven.
- **PostgreSQL 17 en todos los entornos**: `compose.yaml` en local, servicio en el CI y PostgreSQL gestionado en producción.
- Esquema manejado por **Flyway** (`src/main/resources/db/migration`). Hibernate corre con `ddl-auto=validate`: nunca crea ni altera tablas.
- Configuración en `src/main/resources/application.properties` (base, segura por defecto) y `application-prod.properties` (perfil `prod`). No uses YAML.
- Spring Boot 4 trae Jackson 3: el `ObjectMapper` vive en `tools.jackson.databind`. Solo las anotaciones (`@JsonProperty`, `@JsonInclude`) siguen en `com.fasterxml.jackson.annotation`.

## Arquitectura

- Paquete base `com.devsenior.sillon`, organizado por dominio: `compartido/`, `seguridad/`, `auth/`, `salon/`, `usuario/`, `servicio/`, `cliente/`, `cita/`, `publico/`, `pago/`, `chat/`, `ia/`.
- Dentro de cada dominio, una subcarpeta por capa (solo las que el dominio necesite):
  `config/` · `controller/` · `dto/` · `mapper/` · `model/` (entidades JPA, enums y @Embeddable) · `repository/` · `service/`.
- `compartido/` usa `config/`, `dto/`, `excepcion/`, `limite/` (rate limit) y `util/`. `seguridad/` usa `config/`, `filtro/`, `handler/`, `model/` y `service/`.
- Los tests van en el mismo paquete que la clase que prueban.
- Capas: Controller → Service → Repository. El Controller no tiene lógica de negocio.
- DTOs como `record` en `<dominio>/dto`. Nunca expongas entidades JPA en la API.
- Un mapper propio por dominio (`@Component`) en `<dominio>/mapper`, sin librerías de mapeo.
- Inyección por constructor. Nunca `@Autowired` sobre atributos.
- Enums persistidos con `@Enumerated(EnumType.STRING)`.
- Errores con excepciones propias que extienden `ExcepcionApi`, y un único `@RestControllerAdvice` en `compartido/`, con el formato de 4 campos: `codigo`, `error`, `mensaje`, `fecha`.

## Multi-inquilino (obligatorio)

- Todo dato pertenece a un salón. El `salonId` sale siempre de `UsuarioPrincipal` (recibido con `@AuthenticationPrincipal`), nunca de la URL ni del cuerpo.
- Toda búsqueda por id filtra por id y salón a la vez (`findByIdAndSalonId`). Si no aparece: 404.
- Ninguna consulta de listado puede olvidar el filtro por salón.

## Base de datos

- Todo cambio de entidad va con su migración `V<n>__descripcion.sql` en el **mismo** PR.
- Nunca edites una migración ya aplicada: se crea otra.
- Si agregas un valor a un enum persistido, la migración actualiza el `check` de la columna.
- Las columnas que se filtran o se unen con frecuencia llevan índice. PostgreSQL no indexa solas las llaves foráneas.
- Actualizaciones que deben ser atómicas (créditos, marcar un token como usado) se hacen con un `UPDATE … WHERE` condicional en el repositorio, nunca leer, modificar y guardar.

## Seguridad

- Todo endpoint nuevo se declara en `ConfiguracionSeguridad` con el rol mínimo necesario. Nada queda abierto por omisión: `anyRequest().authenticated()` es el último recurso, no el plan.
- Si el endpoint es público o costoso (IA, BCrypt, escritura), agrega su regla en `ReglaLimite` y en `FiltroLimite`.
- Todo valor obligatorio de configuración se valida al arrancar (ver `CorsProperties`, `SesionProperties`, `JwtService`). Con `@ConfigurationProperties`, un `${VARIABLE}` sin valor llega como texto literal: recházalo explícitamente.
- Nunca escribas secretos en el código, en `application*.properties`, en tests ni en logs. Van en `secrets.properties` (que no puedes leer) o en variables de entorno.
- Nunca registres en logs tokens, contraseñas, el mensaje del cliente ni sus datos personales.
- La IP del cliente es `request.getRemoteAddr()`. Nunca leas `X-Forwarded-For` a mano.

## Tiempo

- En servicios y componentes, "hoy" y "ahora" salen del bean `Clock` (zona `America/Bogota`), recibido por constructor: `LocalDateTime.now(clock)` o `clock.instant()`.
- Nunca uses `LocalDate.now()` ni `LocalDateTime.now()` sin argumento. La única excepción son los callbacks `@PrePersist`/`@PreUpdate` de las entidades, que usan `LocalDateTime.now(ZoneId.of("America/Bogota"))`.
- Los instantes técnicos (vencimientos de sesión) usan `Instant`. Las fechas de negocio (citas) usan `LocalDateTime` en hora de Bogotá.
- Los servidores corren en UTC: nada puede depender de la zona del sistema operativo.

## Código

- Clases, métodos y variables en español.
- Sin Lombok: getters y setters explícitos.
- Validación de entrada con Jakarta Validation en los DTO de request.
- Si devuelves una entidad después de modificarla y la respuesta incluye `fechaActualizacion`, guarda con `saveAndFlush`.
- Convierte entidades a DTO dentro del método transaccional del servicio (`open-in-view=false`).
- Si un método `@Transactional` revoca o registra algo y luego lanza una excepción, usa `noRollbackFor` para esa excepción (ver `SesionService.rotar`).

## Tests

- JUnit 5 + Mockito. Los tests de servicios no levantan Spring; los repositorios y la IA son mocks.
- Las fechas en tests usan un `Clock` fijo; los límites de peticiones, un `TimeMeter` manual.
- Ningún test llama a Gemini ni a Stripe.
- Nunca modifiques un test para que pase. Si un test falla, explica si la causa está en el test o en el código y espera la decisión.
- Nombres de método: `metodo_escenario_resultado`.

## IA

- Todo proveedor implementa la interfaz `ProveedorIA` en `ia/`. `AsistenteIA` los recorre en orden (`@Order`).
- Llamadas HTTP con `RestClient`. Sin SDKs de IA ni Spring AI.
- Las URLs de los proveedores se leen de `sillon.ia.*.url`; nunca las escribas fijas en el código.
- Los prompts viven en `src/main/resources/prompts/*.txt` y se cargan con `BibliotecaPrompts`. Nunca escribas un prompt dentro de una clase Java.
- El texto del cliente va siempre como mensaje, nunca dentro de las instrucciones. En el chatbot, el historial va solo en el mensaje del usuario; el nombre del salón, el catálogo y las reglas van en las instrucciones.
- En el recordatorio, los datos verificados van uno por línea con etiqueta (`- Servicio:`, `- Estilista:`, `- Fecha:`, `- Hora:`, `- Valor:`, `- Dirección:`).
- Toda respuesta JSON de la IA se lee con `LectorJsonIA` y se valida antes de usarla.
- Los cálculos (fechas, horas, disponibilidad, precios, créditos) los hace Java, nunca la IA.
- Ninguna llamada a la IA va dentro de una transacción de base de datos.

## Pagos

- El cliente de Stripe se construye con la URL base de `sillon.stripe.url-api`.
- El webhook recibe el cuerpo como `String` crudo; nunca lo conviertas a DTO antes de verificar la firma.
- La firma se verifica con `Webhook.Signature.verifyHeader` de stripe-java y el evento se lee con el `ObjectMapper` de Jackson 3, no con los modelos de Stripe (ver ADR-0009).

## Git

- GitHub Flow: rama desde `main`, PR, CI en verde y *squash and merge*. Nunca push directo a `main`.
- Conventional Commits en español: `feat:`, `fix:`, `chore:`, `docs:`, `test:`.
- Detalles en `CONTRIBUTING.md`.

## Límites

- No agregues dependencias al `pom.xml` sin preguntar.
- No cambies `application*.properties`, el CI (`.github/`), el `Dockerfile` ni `compose.yaml` sin que se pida explícitamente.
- No edites migraciones ya aplicadas.
- Si algo de la SPEC es ambiguo, pregunta antes de suponer.
