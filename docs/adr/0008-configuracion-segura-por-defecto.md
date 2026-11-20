# ADR-0008 · Configuración segura por defecto y perfil prod

- **Estado:** Aceptada
- **Fecha:** 2026-11-19 (B1)

## Contexto

`application.properties` traía un valor por defecto para el secreto JWT. Ese valor era público en GitHub: si faltaba la variable en producción, cualquiera podía firmar un token de ADMIN.

## Decisión

- La **base es segura**: el secreto JWT no tiene valor por defecto en ningún entorno. En local sale de `secrets.properties`.
- El perfil `prod` agrega lo propio de producción: base de datos, URL y CORS **sin valores por defecto**, Swagger apagado, errores sin detalles y cookie `Secure`.
- Los valores obligatorios se validan **explícitamente** al arrancar, porque con `@ConfigurationProperties` un `${VAR}` sin valor llega como texto literal y no falla solo.

## Alternativas consideradas

- **Secreto solo en el perfil `prod`:** si se olvida activar el perfil, se vuelve al secreto conocido.
- **Confiar en que Spring falle ante un placeholder sin resolver:** solo pasa con `@Value`, no con `@ConfigurationProperties`.

## Consecuencias

- Olvidar el perfil `prod` deja Swagger visible, pero nunca un secreto conocido.
- Cada propiedad obligatoria nueva necesita su validación y su test (ver `JwtServiceTest`, `CorsPropertiesTest`).
