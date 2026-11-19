package com.devsenior.sillon.auth.controller;

import com.devsenior.sillon.auth.dto.AuthResponse;
import com.devsenior.sillon.auth.dto.LoginRequest;
import com.devsenior.sillon.auth.dto.RegistroRequest;
import com.devsenior.sillon.auth.dto.SesionIniciada;
import com.devsenior.sillon.auth.service.AuthService;
import com.devsenior.sillon.compartido.excepcion.FabricaErrores;
import com.devsenior.sillon.compartido.excepcion.SesionInvalidaException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1 Autenticación", description = "Registro, inicio de sesión, renovación y cierre de sesión")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieRefresco cookieRefresco;
    private final FabricaErrores fabricaErrores;

    public AuthController(AuthService authService, CookieRefresco cookieRefresco, FabricaErrores fabricaErrores) {
        this.authService = authService;
        this.cookieRefresco = cookieRefresco;
        this.fabricaErrores = fabricaErrores;
    }

    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registrar(@Valid @RequestBody RegistroRequest request) {
        return conCookie(HttpStatus.CREATED, authService.registrar(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return conCookie(HttpStatus.OK, authService.login(request));
    }

    @Operation(summary = "Renueva el access token usando la cookie httpOnly de sesión (sin body)")
    @PostMapping("/refresh")
    public ResponseEntity<?> refrescar(
            @CookieValue(name = CookieRefresco.NOMBRE, required = false) String refresco) {
        try {
            return conCookie(HttpStatus.OK, authService.refrescar(refresco));
        } catch (SesionInvalidaException ex) {
            // 401 en el formato único de error, borrando la cookie que ya no sirve
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookieRefresco.borrar().toString())
                    .body(fabricaErrores.crear(HttpStatus.UNAUTHORIZED, ex.getMessage()));
        }
    }

    @Operation(summary = "Cierra la sesión de este dispositivo (idempotente)")
    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(
            @CookieValue(name = CookieRefresco.NOMBRE, required = false) String refresco) {
        authService.cerrarSesion(refresco);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresco.borrar().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> conCookie(HttpStatus estado, SesionIniciada sesion) {
        return ResponseEntity.status(estado)
                .header(HttpHeaders.SET_COOKIE, cookieRefresco.crear(sesion.refresco()).toString())
                .body(sesion.respuesta());
    }
}
