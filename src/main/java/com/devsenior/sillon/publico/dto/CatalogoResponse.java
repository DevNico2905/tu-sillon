package com.devsenior.sillon.publico.dto;

import com.devsenior.sillon.servicio.dto.ServicioResponse;
import java.time.LocalTime;
import java.util.List;

/**
 * Cara pública del salón: sin correos, sin contraseñas, sin datos de planes ni créditos.
 * {@code horario} es el texto para mostrar; {@code horaApertura}, {@code horaCierre} y {@code abreDomingos}
 * son el mismo horario en datos, para que el front arme el calendario sin interpretar el texto (SPEC R42).
 */
public record CatalogoResponse(
        String nombre,
        String slug,
        String ciudad,
        String direccion,
        String telefono,
        String horario,
        LocalTime horaApertura,
        LocalTime horaCierre,
        boolean abreDomingos,
        List<ServicioResponse> servicios,
        List<EstilistaPublicoResponse> estilistas
) {
}
