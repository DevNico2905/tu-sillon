package com.devsenior.sillon.compartido.limite;

import com.devsenior.sillon.compartido.excepcion.LimiteExcedidoException;
import com.devsenior.sillon.seguridad.handler.EscritorErroresSeguridad;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Aplica ReglaLimite antes de la autenticación: un ataque no nos hace gastar BCrypt ni consultas.
 * La IP sale de getRemoteAddr(): en producción la resuelve Tomcat (forward-headers-strategy=native),
 * que recorre X-Forwarded-For de derecha a izquierda y no se deja engañar por valores del cliente.
 * El límite por correo del login vive en AuthService porque necesita el body.
 */
@Component
public class FiltroLimite extends OncePerRequestFilter {

    private static final Pattern RUTA_SALON = Pattern.compile("^/api/publico/salones/([^/]+)/(chat|reservas)$");

    private final Limitador limitador;
    private final EscritorErroresSeguridad escritorErrores;

    public FiltroLimite(Limitador limitador, EscritorErroresSeguridad escritorErrores) {
        this.limitador = limitador;
        this.escritorErrores = escritorErrores;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        try {
            aplicarReglas(request);
        } catch (LimiteExcedidoException ex) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosEspera()));
            escritorErrores.escribir(response, HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
            return;
        }
        cadena.doFilter(request, response);
    }

    private void aplicarReglas(HttpServletRequest request) {
        String metodo = request.getMethod();
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        String ip = request.getRemoteAddr();

        if ("POST".equals(metodo)) {
            switch (ruta) {
                case "/api/auth/login" -> limitador.consumir(ReglaLimite.LOGIN_IP, ip);
                case "/api/auth/registro" -> limitador.consumir(ReglaLimite.REGISTRO_IP, ip);
                case "/api/auth/refresh" -> limitador.consumir(ReglaLimite.REFRESH_IP, ip);
                default -> aplicarReglasDeSalon(ruta, ip);
            }
        } else if ("GET".equals(metodo) && ruta.startsWith("/api/publico/")) {
            limitador.consumir(ReglaLimite.PUBLICO_IP, ip);
        }
    }

    private void aplicarReglasDeSalon(String ruta, String ip) {
        Matcher coincidencia = RUTA_SALON.matcher(ruta);
        if (!coincidencia.matches()) {
            return;
        }
        // En minúsculas: cambiar mayúsculas en la URL no abre un balde nuevo para el mismo salón
        String salon = coincidencia.group(1).toLowerCase(Locale.ROOT);
        if ("chat".equals(coincidencia.group(2))) {
            limitador.consumir(ReglaLimite.CHAT_IP, ip);
            limitador.consumir(ReglaLimite.CHAT_SALON, salon);
        } else {
            limitador.consumir(ReglaLimite.RESERVA_IP, ip);
            limitador.consumir(ReglaLimite.RESERVA_SALON, salon);
        }
    }
}
