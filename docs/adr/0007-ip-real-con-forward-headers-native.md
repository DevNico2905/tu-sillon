# ADR-0007 · IP real del cliente con forward-headers-strategy=native

- **Estado:** Aceptada
- **Fecha:** 2026-11-19 (B4, corrige B1)

## Contexto

Detrás de un proxy, la IP del cliente llega en `X-Forwarded-For`. En B1 se configuró `framework`, que toma el **primer** valor de ese header, y el primer valor lo escribe el cliente. Con eso, el rate limit por IP se esquivaba cambiando el header en cada petición.

## Decisión

`server.forward-headers-strategy=native` en el perfil `prod`. Tomcat (`RemoteIpValve`) recorre el header **de derecha a izquierda**, salta solo proxies de red privada y toma la primera IP pública. La aplicación usa siempre `request.getRemoteAddr()`.

## Alternativas consideradas

- **`framework`:** se puede falsificar.
- **Leer `X-Forwarded-For` a mano:** es fácil equivocarse con la posición correcta.
- **Configurar un número fijo de proxies:** se rompe si la plataforma cambia su cadena de proxies.

## Consecuencias

- La IP no se puede falsificar **mientras nadie llegue a la app sin pasar por el proxy**. Es un requisito de despliegue (ver `operacion.md`).
- Si el proxy de la plataforma no usa IPs privadas, Tomcat no confiará en él: todos los clientes parecerían tener la IP del proxy. Se detecta en los logs.
