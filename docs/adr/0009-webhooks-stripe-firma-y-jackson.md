# ADR-0009 · Webhooks de Stripe: firma con stripe-java, lectura con Jackson

- **Estado:** Aceptada
- **Fecha:** 2026-11-18 (entrega del curso)

## Contexto

Los eventos de Stripe llegan con el `api_version` de la cuenta, que puede no coincidir con el de la librería stripe-java. En ese caso, deserializar con los modelos de Stripe deja el objeto vacío. Además, cualquier transformación del cuerpo antes de verificar la firma la invalida.

## Decisión

- El controlador recibe el cuerpo como `String` crudo.
- La firma se verifica con `Webhook.Signature.verifyHeader` (tolerancia de 300 s).
- El evento se lee con el `ObjectMapper` de Jackson 3, en un `record` con solo los campos necesarios.
- Idempotencia: el id del evento se guarda en `eventos_stripe` en la misma transacción que el efecto.

## Alternativas consideradas

- **`Webhook.constructEvent` y los modelos de Stripe:** fallan en silencio cuando cambia la versión de la API.
- **Fijar el `api_version` de la cuenta igual al de la librería:** es frágil ante cualquier actualización.

## Consecuencias

- Inmune a cambios de versión de la API de Stripe.
- Si se necesita un campo nuevo del evento, hay que agregarlo al `record` `EventoWebhook`.
