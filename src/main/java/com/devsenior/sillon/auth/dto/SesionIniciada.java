package com.devsenior.sillon.auth.dto;

/** Uso interno entre servicio y controlador: el JSON de respuesta y el valor de la cookie. Nunca se serializa. */
public record SesionIniciada(AuthResponse respuesta, String refresco) {
}
