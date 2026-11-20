# 💈 Sillón

Agenda inteligente para barberías y salones de belleza. Es un **SaaS multi-inquilino** en Java: cada salón configura su horario, servicios y equipo; sus clientes reservan en línea solo en horarios realmente libres; y la IA redacta recordatorios y responde preguntas con el catálogo real del salón.

> Nació como proyecto final de los módulos 4 y 5 del curso *Java AI Engineer* (Dev Senior Code) y hoy sigue como proyecto personal: endurecimiento de seguridad, PostgreSQL con migraciones y, a continuación, un frontend en Next.js (repositorio aparte).

---

## 🧱 Stack

| Pieza | Tecnología |
|---|---|
| Lenguaje y framework | Java 17, Spring Boot 4.1, Maven |
| Persistencia | Spring Data JPA · PostgreSQL 17 en todos los entornos · migraciones con Flyway |
| Seguridad | Spring Security · access token JWT (15 min) · refresh token en cookie httpOnly con rotación · BCrypt · rate limit con Bucket4j · CORS por lista blanca |
| Pagos | Stripe (modo prueba): Checkout, suscripción, pago único y webhook firmado e idempotente |
| IA | Gemini por HTTP (formato OpenAI) con `RestClient`, respaldo con formato Claude, créditos por respuesta |
| Documentación de la API | springdoc-openapi (Swagger UI, solo en desarrollo) |
| Calidad | JUnit 5 + Mockito · GitHub Actions en cada PR · Dependabot |
| Contenedor | Docker en dos etapas, usuario sin privilegios |

---

## ▶️ Correrlo en local (5 minutos)

**Requisitos:** Java 17 o superior, Docker Desktop.

```bash
# 1. Base de datos (PostgreSQL 17 en Docker, solo accesible desde tu máquina)
docker compose up -d

# 2. Secretos locales: copia la plantilla y pon, como mínimo, sillon.jwt.secreto
cp secrets.example.properties secrets.properties
echo "sillon.jwt.secreto=$(openssl rand -base64 48)" >> secrets.properties

# 3. Arranca la API (Flyway crea el esquema al iniciar)
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html

Para la IA y los pagos hacen falta más llaves; está todo en [`docs/configuracion.md`](docs/configuracion.md).

**Tests** (necesitan la base de `compose` encendida):

```bash
./mvnw verify
```

---

## 📖 Documentación

| Documento | Qué encuentras |
|---|---|
| [`SPEC.md`](SPEC.md) | **Fuente de verdad del negocio:** roles, reglas R1–R39, contrato de la API |
| [`docs/arquitectura.md`](docs/arquitectura.md) | Dominios, capas, multi-inquilino y el recorrido de una petición |
| [`docs/seguridad.md`](docs/seguridad.md) | Modelo de amenazas, sesiones, rate limit, CORS y la revisión de seguridad |
| [`docs/base-de-datos.md`](docs/base-de-datos.md) | Diagrama entidad-relación, índices y reglas de Flyway |
| [`docs/configuracion.md`](docs/configuracion.md) | Todas las variables y propiedades, por perfil |
| [`docs/integraciones.md`](docs/integraciones.md) | Stripe e IA: flujos, fallos y costos |
| [`docs/operacion.md`](docs/operacion.md) | Runbook: desplegar, rotar secretos y "qué hago si…" |
| [`docs/adr/`](docs/adr/) | Decisiones de arquitectura: qué se decidió, por qué y qué se aceptó a cambio |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Cómo trabajar en el repo: ramas, commits, PRs, migraciones y tests |
| [`CHANGELOG.md`](CHANGELOG.md) | Qué cambió y cuándo |
| [`AGENTS.md`](AGENTS.md) | Reglas para asistentes de código (Claude Code y similares) |

---

## 📚 Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/auth/registro` | Público |
| POST | `/api/auth/login` | Público |
| POST | `/api/auth/refresh` | Público (cookie de sesión) |
| POST | `/api/auth/logout` | Público (cookie de sesión) |
| GET | `/api/salon` | DUENO |
| PUT | `/api/salon/configuracion` | DUENO |
| POST · GET | `/api/servicios` | POST: DUENO · GET: DUENO, ESTILISTA |
| PUT | `/api/servicios/{id}` | DUENO |
| POST · GET | `/api/estilistas` | DUENO |
| GET | `/api/clientes` | DUENO |
| POST | `/api/citas` | DUENO |
| GET | `/api/citas?fecha=` | DUENO (todas), ESTILISTA (las suyas) |
| PATCH | `/api/citas/{id}/estado` | DUENO, ESTILISTA (solo COMPLETADA o NO_ASISTIO) |
| POST | `/api/citas/{id}/recordatorio` | DUENO · gasta 1 crédito de IA |
| GET | `/api/publico/salones/{slug}` | Público |
| GET | `/api/publico/salones/{slug}/disponibilidad?servicioId=&estilistaId=&fecha=` | Público |
| POST | `/api/publico/salones/{slug}/reservas` | Público |
| POST | `/api/publico/salones/{slug}/chat` | Público · gasta 1 crédito del salón |
| GET | `/api/solicitudes` | DUENO |
| PATCH | `/api/solicitudes/{id}/atendida` | DUENO |
| POST | `/api/pagos/suscripcion` · `/api/pagos/creditos` | DUENO |
| POST | `/api/pagos/webhook` | Stripe (firma `Stripe-Signature`) |
| GET | `/api/pagos/resultado?estado=` | Público |
| GET | `/api/admin/salones` | ADMIN |

Todos los errores usan el mismo formato: `codigo`, `error`, `mensaje`, `fecha`. El contrato completo está en [`SPEC.md`](SPEC.md#9-contrato-de-la-api).

---

## 🧪 Colección de Postman

`postman/` tiene la colección con pruebas automáticas en cada petición y los entornos `local` y `produccion`. Viene del proyecto del curso: cubre el negocio completo, pero no usa todavía la cookie de sesión (refresh/logout).
