package com.devsenior.sillon.salon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos del salón que el dueño puede corregir (R44). Mismos límites que el registro.
 * El slug no está aquí a propósito: cambiarlo rompería los enlaces que el salón ya compartió.
 */
public record DatosSalonRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 100, message = "máximo 100 caracteres") String nombre,
        @NotBlank(message = "es obligatoria") @Size(max = 60, message = "máximo 60 caracteres") String ciudad,
        @NotBlank(message = "es obligatoria") @Size(max = 150, message = "máximo 150 caracteres") String direccion,
        @NotBlank(message = "es obligatorio") @Size(max = 30, message = "máximo 30 caracteres") String telefono
) {
}
