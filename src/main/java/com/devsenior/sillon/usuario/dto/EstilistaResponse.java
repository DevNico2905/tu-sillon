package com.devsenior.sillon.usuario.dto;

import java.util.List;

/** Nunca incluye la contraseña. Los servicios van como nombres (para mostrar) y como ids (para editar). */
public record EstilistaResponse(
        Long id,
        String nombre,
        String email,
        boolean activo,
        List<String> servicios,
        List<Long> servicioIds
) {
}
