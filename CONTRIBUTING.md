# Cómo contribuir a Sillón

Esta guía es para cualquier persona que trabaje en el repositorio, incluido tu yo del futuro.

## Antes de empezar

1. Lee [`SPEC.md`](SPEC.md): define qué hace el sistema. Si el código y la SPEC no coinciden, se corrige el código o se discute la regla. **Nunca se ajusta un test para que pase.**
2. Lee [`docs/arquitectura.md`](docs/arquitectura.md) para saber dónde va cada cosa.
3. Monta el entorno local siguiendo el [`README.md`](README.md) (sección "Correrlo en local").

## Flujo de trabajo: GitHub Flow

```
main  ●──────────────●────────────────●────
       \            / squash merge   /
        ●──●──●────●                /
        feat/mi-cambio  (PR + CI verde)
```

1. **`main` siempre funciona** y es lo que se despliega. Está protegida: no acepta push directo ni force push.
2. **Una rama por cambio**, creada desde `main` actualizada:
   ```bash
   git checkout main && git pull
   git checkout -b feat/descripcion-corta
   ```
3. **Todo entra por Pull Request.** El CI (`tests`) debe salir en verde para poder hacer merge.
4. **Squash and merge**: cada PR queda como un solo commit en `main`.
5. **Borra la rama** después del merge. Las ramas viven horas o días, no semanas.

### Nombres de rama

| Prefijo | Uso | Ejemplo |
|---|---|---|
| `feat/` | Funcionalidad nueva | `feat/refresh-token` |
| `fix/` | Corrección | `fix/config-produccion` |
| `chore/` | Configuración, CI, dependencias, infraestructura | `chore/flyway` |
| `docs/` | Solo documentación | `docs/documentacion-inicial` |
| `test/` | Solo tests | `test/disponibilidad-bordes` |

### Mensajes de commit: Conventional Commits

```
<tipo>: <qué cambia, en español y en presente>
```

Ejemplos reales del repo:

```
feat: refresh token en cookie httpOnly con rotación, detección de reuso y logout
fix: secreto JWT obligatorio y perfil prod (Swagger apagado, proxy, sin defaults)
chore: PostgreSQL en local y CI, migraciones con Flyway y ddl-auto=validate
```

## Checklist de un PR

La plantilla del PR la trae, pero en resumen:

- [ ] `./mvnw verify` en verde en local.
- [ ] Probé el caso feliz **y** el caso de error.
- [ ] No hay secretos ni llaves en el diff.
- [ ] Si toqué un endpoint, revisé quién puede llamarlo (`ConfiguracionSeguridad`) y si necesita una regla de rate limit (`ReglaLimite`).
- [ ] Si cambié una entidad, agregué la migración Flyway correspondiente.
- [ ] Si cambié una regla de negocio, actualicé `SPEC.md`.
- [ ] Si tomé una decisión de arquitectura, escribí un ADR en `docs/adr/`.
- [ ] Si agregué una variable de configuración, la documenté en `docs/configuracion.md`.

## Cambios en la base de datos

El esquema lo maneja **Flyway**, no Hibernate (`ddl-auto=validate`).

1. Crea `src/main/resources/db/migration/V<n>__descripcion.sql`, con **dos** guiones bajos. Con uno solo, Flyway ignora el archivo sin avisar.
2. En el **mismo PR**, cambia la entidad.
3. **Nunca edites una migración ya aplicada**, aunque solo sea un comentario: Flyway detecta el cambio de checksum y la app no arranca. Para corregir algo, se agrega otra migración.
4. Si agregas un valor a un enum persistido (`EstadoCita`, `Rol`…), la migración debe actualizar el `check` de esa columna.

`validate` detecta tablas o columnas faltantes y tipos incompatibles, pero **no** longitudes, `nullable`, índices ni constraints: esos dependen de tu revisión. Detalles en [`docs/base-de-datos.md`](docs/base-de-datos.md).

## Tests

- JUnit 5 + Mockito. Los tests de servicios no levantan Spring; los repositorios y la IA son mocks.
- Fechas con un `Clock` fijo. Nunca `LocalDateTime.now()` en un test.
- Ningún test llama a Gemini ni a Stripe.
- Nombres: `metodo_escenario_resultado` (`rotar_reusoFueraDeLaGracia_revocaTodaLaFamilia`).
- `SillonApplicationTests.contextLoads` levanta la app completa contra PostgreSQL: aplica las migraciones y valida el esquema. Si falla, casi siempre es una migración o una configuración.

## Seguridad

- Los secretos viven en `secrets.properties` (local, ignorado por Git) o en variables de entorno (producción). Nunca en el código, en `application*.properties`, en issues ni en prompts.
- Si un secreto llega a un commit, **rótalo**, aunque hagas `git reset`: el historial ya pudo copiarse. Ver [`docs/operacion.md`](docs/operacion.md).
- Más contexto en [`docs/seguridad.md`](docs/seguridad.md).

## Uso de asistentes de IA

Si usas Claude Code u otro asistente, este lee [`AGENTS.md`](AGENTS.md). `.claude/settings.json` le impide leer archivos de secretos. Revisa todo lo que genere como revisarías el código de otra persona.
