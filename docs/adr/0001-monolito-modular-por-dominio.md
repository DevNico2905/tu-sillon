# ADR-0001 · Monolito modular organizado por dominio

- **Estado:** Aceptada
- **Fecha:** 2026-11-18 (entrega del curso)

## Contexto

Sillón tiene una docena de áreas de negocio (salones, agenda, pagos, IA…) y lo desarrolla una sola persona. La primera versión organizaba el código por capa técnica (`controller/`, `service/`…), y encontrar todo lo de un tema obligaba a saltar entre carpetas.

## Decisión

Una sola aplicación Spring Boot con un paquete por **dominio** (`salon`, `cita`, `pago`…) y, dentro de cada uno, una subcarpeta por **capa** (`controller`, `dto`, `mapper`, `model`, `repository`, `service`, `config`). `compartido`, `seguridad` e `ia` son transversales: los dominios dependen de ellos, nunca al revés.

## Alternativas consideradas

- **Paquetes por capa:** es más simple al empezar, pero cada cambio toca cinco carpetas y no muestra los límites del negocio.
- **Microservicios:** traen despliegue, red, consistencia y observabilidad distribuidas sin ningún beneficio a esta escala.
- **Paquetes por dominio sin subcarpetas:** quedan carpetas con 20 archivos mezclados.

## Consecuencias

- Todo lo de un tema vive junto, y un dominio se podría extraer más adelante si hiciera falta.
- La regla de dependencias no la obliga el compilador. Si se rompe a menudo, se puede agregar ArchUnit.
