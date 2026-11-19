package com.devsenior.sillon.auth.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devsenior.sillon.auth.config.SesionProperties;
import org.junit.jupiter.api.Test;

class CookieRefrescoTest {

    @Test
    void crear_tieneTodosLosAtributosDeSeguridad() {
        String cabecera = new CookieRefresco(new SesionProperties(7, 30, 10, true)).crear("abc").toString();

        assertTrue(cabecera.startsWith("sillon_refresh=abc"));
        assertTrue(cabecera.contains("HttpOnly"));
        assertTrue(cabecera.contains("Secure"));
        assertTrue(cabecera.contains("SameSite=Strict"));
        assertTrue(cabecera.contains("Path=/api/auth"));
        assertTrue(cabecera.contains("Max-Age=604800"));
    }

    @Test
    void crear_enLocal_noEsSecure() {
        String cabecera = new CookieRefresco(new SesionProperties(7, 30, 10, false)).crear("abc").toString();

        assertFalse(cabecera.contains("Secure"));
        assertTrue(cabecera.contains("HttpOnly"));
    }

    @Test
    void borrar_vaciaLaCookieYLaVence() {
        String cabecera = new CookieRefresco(new SesionProperties(7, 30, 10, true)).borrar().toString();

        assertTrue(cabecera.startsWith("sillon_refresh=;"));
        assertTrue(cabecera.contains("Max-Age=0"));
        assertTrue(cabecera.contains("Path=/api/auth"));
    }
}
