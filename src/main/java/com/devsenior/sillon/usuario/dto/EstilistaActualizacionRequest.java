package com.devsenior.sillon.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Edición de un estilista por el dueño (R43). El correo no cambia: es con lo que inicia sesión.
 * La contraseña es opcional: si viene, la reemplaza (sirve cuando el estilista la olvida).
 */
public record EstilistaActualizacionRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 100, message = "máximo 100 caracteres") String nombre,
        @NotEmpty(message = "debe tener al menos un servicio") List<@NotNull(message = "no puede tener ids vacíos") Long> servicioIds,
        @NotNull(message = "es obligatorio") Boolean activo,
        @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres") String password
) {
}
