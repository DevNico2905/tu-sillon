# ADR-0010 · IA por HTTP, proveedores en cadena y créditos atómicos

- **Estado:** Aceptada
- **Fecha:** 2026-11-18 (entrega del curso)

## Contexto

Cada respuesta de IA cuesta dinero, los proveedores fallan o se saturan, y las llamadas tardan segundos.

## Decisión

- HTTP directo con `RestClient`, **sin SDKs ni Spring AI**. Interfaz `ProveedorIA`, con Gemini (formato OpenAI) primero y Claude de respaldo; `AsistenteIA` los recorre en orden.
- **Créditos:** se verifican antes de llamar (402) y se consumen solo si hubo respuesta, con un `UPDATE … WHERE creditos_ia > 0` atómico.
- **La IA nunca va dentro de una transacción** de base de datos.
- La IA solo redacta. Los cálculos (fechas, precios, disponibilidad) los hace Java, y el filtro anti-promesas también es Java.

## Alternativas consideradas

- **SDK de cada proveedor o Spring AI:** más dependencias y menos control sobre los tiempos de espera y los errores.
- **Leer los créditos, restar y guardar:** se pierden descuentos con peticiones simultáneas.
- **Llamar a la IA dentro de la transacción:** mantiene una conexión ocupada segundos por cada mensaje.

## Consecuencias

- Cambiar o agregar un proveedor es implementar una interfaz.
- Una respuesta con JSON inválido consume el crédito (502): la IA trabajó y costó tokens.
