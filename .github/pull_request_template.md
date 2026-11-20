## Ticket
<!-- B1, B2... o una línea de contexto -->

## Qué cambia

## Cómo lo probé
- [ ] `./mvnw verify` en local
- [ ] Probé el caso feliz y el caso de error

## Seguridad
- [ ] No agrega secretos ni llaves
- [ ] Si toca endpoints, revisé quién puede llamarlos y si necesitan rate limit

## Documentación y base de datos
- [ ] Si cambié una entidad, agregué la migración Flyway correspondiente
- [ ] Si cambié una regla de negocio, actualicé `SPEC.md`
- [ ] Si tomé una decisión de arquitectura, agregué un ADR en `docs/adr/`
- [ ] Si agregué configuración, la documenté en `docs/configuracion.md`
