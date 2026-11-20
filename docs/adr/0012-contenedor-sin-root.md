# ADR-0012 · Contenedor sin root

- **Estado:** Aceptada
- **Fecha:** 2026-11-20 (B6)

## Contexto

El contenedor corría como `root`. Una vulnerabilidad en la app o en una librería daría root dentro del contenedor.

## Decisión

- Imagen de dos etapas: Maven compila y `eclipse-temurin:17-jre` ejecuta.
- Usuario `sillon` con **UID numérico 10001** (`USER 10001:10001`), para que plataformas como Kubernetes o ECS puedan verificar que no es root.
- El `.jar` es propiedad de root: la app no puede modificarse a sí misma.
- `-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError`.

## Alternativas consideradas

- **Imagen *distroless*:** más pequeña y sin shell, pero más difícil de diagnosticar. Queda como mejora futura.

## Consecuencias

- Un atacante que comprometa la app tiene mucho menos alcance.
- La app no puede escribir en `/app`: si algún día necesita archivos temporales, se usa `/tmp` o un volumen.
