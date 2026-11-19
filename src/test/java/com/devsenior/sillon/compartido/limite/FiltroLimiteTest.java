package com.devsenior.sillon.compartido.limite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devsenior.sillon.compartido.excepcion.FabricaErrores;
import com.devsenior.sillon.seguridad.handler.EscritorErroresSeguridad;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class FiltroLimiteTest {

    private FiltroLimite filtro;

    @BeforeEach
    void setUp() {
        EscritorErroresSeguridad escritor =
                new EscritorErroresSeguridad(new FabricaErrores(Clock.systemUTC()), new ObjectMapper());
        filtro = new FiltroLimite(new Limitador(), escritor);
    }

    @Test
    void sextoLoginDeLaMismaIp_429ConRetryAfterYFormatoDeError() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(200, ejecutar("POST", "/api/auth/login", "1.1.1.1").getStatus());
        }

        MockHttpServletResponse respuesta = ejecutar("POST", "/api/auth/login", "1.1.1.1");

        assertEquals(429, respuesta.getStatus());
        assertEquals("12", respuesta.getHeader("Retry-After"));
        assertTrue(respuesta.getContentAsString().contains("\"codigo\":429"));
    }

    @Test
    void otraIpNoSeAfecta() throws Exception {
        for (int i = 0; i < 5; i++) {
            ejecutar("POST", "/api/auth/login", "1.1.1.1");
        }

        assertEquals(200, ejecutar("POST", "/api/auth/login", "2.2.2.2").getStatus());
    }

    @Test
    void chat_elLimiteDelSalonAplicaAunqueCadaPeticionVengaDeOtraIp() throws Exception {
        for (int i = 0; i < 100; i++) {
            assertEquals(200, ejecutar("POST", "/api/publico/salones/barberia/chat", "10.0.0." + i).getStatus());
        }

        assertEquals(429, ejecutar("POST", "/api/publico/salones/barberia/chat", "9.9.9.9").getStatus());
        // Otro salón no se ve afectado
        assertEquals(200, ejecutar("POST", "/api/publico/salones/otro-salon/chat", "9.9.9.9").getStatus());
    }

    @Test
    void chat_cambiarMayusculasDelSlugNoAbreUnBaldeNuevo() throws Exception {
        for (int i = 0; i < 100; i++) {
            ejecutar("POST", "/api/publico/salones/barberia/chat", "10.0.0." + i);
        }

        assertEquals(429, ejecutar("POST", "/api/publico/salones/BARBERIA/chat", "9.9.9.9").getStatus());
    }

    @Test
    void reservas_seisEnDiezMinutosDesdeLaMismaIp_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            ejecutar("POST", "/api/publico/salones/barberia/reservas", "1.1.1.1");
        }

        assertEquals(429, ejecutar("POST", "/api/publico/salones/barberia/reservas", "1.1.1.1").getStatus());
    }

    @Test
    void endpointsSinRegla_pasanSiempre() throws Exception {
        for (int i = 0; i < 200; i++) {
            MockHttpServletResponse respuesta = ejecutar("GET", "/api/citas", "1.1.1.1");
            assertEquals(200, respuesta.getStatus());
            assertNull(respuesta.getHeader("Retry-After"));
        }
    }

    private MockHttpServletResponse ejecutar(String metodo, String ruta, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
