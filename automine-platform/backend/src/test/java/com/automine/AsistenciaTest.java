package com.automine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.automine.asistencia.CalculadoraJornada;
import com.automine.asistencia.CalculadoraJornada.Evento;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Asistencia con rostro registrada por SG-SST y calculo de horas extra. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AsistenciaTest {

    private static final String PASS = "Automine2026*";
    private static final String NUEVA = "MiClavePropia2026";
    private static final CalculadoraJornada.Parametros P = new CalculadoraJornada.Parametros(8, 19, 6);
    private static final String PLANTILLA = "[0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0]";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    String admin;
    String siso;
    String rrhh;
    long empleadoId;
    final LocalDate ayer = LocalDate.now().minusDays(1);

    @BeforeAll
    void preparar() throws Exception {
        admin = login("admin@automine.co", PASS);
        empleadoId = crearEmpleado("6161616161", "Gloria", "Toro");
        crearUsuario("siso.asis@automine.co", "SGSST");
        crearUsuario("th.asis@automine.co", "RRHH");
        siso = primerIngreso("siso.asis@automine.co");
        rrhh = primerIngreso("th.asis@automine.co");
    }

    // ------------------------------ calculo de jornada ------------------------------

    @Test
    void horasExtraDiurnasNocturnasYDominicales() {
        // Lunes 2 de marzo de 2026: 6:00 a 18:00 -> 8 ordinarias y 4 extra diurnas
        var r = CalculadoraJornada.calcular(List.of(new Evento("ENTRADA", LocalDateTime.of(2026, 3, 2, 6, 0)),
                new Evento("SALIDA", LocalDateTime.of(2026, 3, 2, 18, 0))), P);
        assertThat(r.horas()).isEqualByComparingTo("12");
        assertThat(r.extraDiurna()).isEqualByComparingTo("4");
        assertThat(r.extraNocturna()).isZero();
        // Martes 14:00 a 23:00: 8 ordinarias (14-22) y 1 extra nocturna (desde las 7 p. m.)
        var n = CalculadoraJornada.calcular(List.of(new Evento("ENTRADA", LocalDateTime.of(2026, 3, 3, 14, 0)),
                new Evento("SALIDA", LocalDateTime.of(2026, 3, 3, 23, 0))), P);
        assertThat(n.extraNocturna()).isEqualByComparingTo("1");
        // Domingo 8 de marzo 6:00 a 16:00: 2 extra dominicales
        var d = CalculadoraJornada.calcular(List.of(new Evento("ENTRADA", LocalDateTime.of(2026, 3, 8, 6, 0)),
                new Evento("SALIDA", LocalDateTime.of(2026, 3, 8, 16, 0))), P);
        assertThat(d.extraDominical()).isEqualByComparingTo("2");
        // Entrada sin salida: inconsistencia, no suma horas
        var i = CalculadoraJornada.calcular(List.of(new Evento("ENTRADA", LocalDateTime.of(2026, 3, 4, 6, 0))), P);
        assertThat(i.inconsistencias()).isEqualTo(1);
        assertThat(i.horas()).isZero();
    }

    // ------------------------------ flujo ------------------------------

    @Test
    @Order(1)
    void elRostroSoloSeRegistraConAutorizacion() throws Exception {
        mvc.perform(rostro(siso, false)).andExpect(status().isBadRequest());
        mvc.perform(rostro(siso, true)).andExpect(status().isOk()).andExpect(jsonPath("$.registrado").value(true));
        JsonNode p = read(call(get("/AutomineApi/Faces"), siso, null).andExpect(status().isOk()));
        assertThat(p.findValuesAsText("empleadoId")).contains(String.valueOf(empleadoId));
        call(get("/AutomineApi/Faces"), rrhh, null).andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    void laSisoMarcaEntradaYSalidaConElRostro() throws Exception {
        JsonNode e = read(mvc.perform(marca(siso, "ENTRADA", ayer.atTime(6, 0), "0.95", "disp-1", true, null))
                .andExpect(status().isCreated()));
        assertThat(e.get("metodo").asText()).isEqualTo("FACIAL");
        // Reenvio desde el celular sin conexion: no duplica
        JsonNode dup = read(mvc.perform(marca(siso, "ENTRADA", ayer.atTime(6, 0), "0.95", "disp-1", true, null))
                .andExpect(status().isCreated()));
        assertThat(dup.get("id").asLong()).isEqualTo(e.get("id").asLong());
        JsonNode s = read(mvc.perform(marca(siso, "SALIDA", ayer.atTime(18, 0), "0.55", "disp-2", true, null))
                .andExpect(status().isCreated()));
        assertThat(s.get("metodo").asText()).isEqualTo("VERIFICACION_MANUAL");
        // Manual sin foto exige motivo; RRHH no marca
        mvc.perform(marca(siso, "ENTRADA", LocalDateTime.now(), null, "disp-3", false, null)).andExpect(status().isBadRequest());
        mvc.perform(marca(rrhh, "ENTRADA", LocalDateTime.now(), "0.9", "disp-4", true, null)).andExpect(status().isForbidden());
        call(get("/AutomineApi/Attendance?fecha=" + ayer + "&empleadoId=" + empleadoId), rrhh, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @Order(3)
    void elResumenCalculaHorasExtraYSePasaALaNomina() throws Exception {
        JsonNode r = read(call(get("/AutomineApi/Attendance/Summary?anio=" + ayer.getYear() + "&mes=" + ayer.getMonthValue()
                + "&empleadoId=" + empleadoId), rrhh, null).andExpect(status().isOk()));
        JsonNode g = r.get(0);
        assertThat(g.get("horas").decimalValue()).isEqualByComparingTo("12");
        BigDecimal extras = g.get("extraDiurna").decimalValue().add(g.get("extraNocturna").decimalValue())
                .add(g.get("extraDominical").decimalValue());
        assertThat(extras).isEqualByComparingTo("4");
        String url = "/AutomineApi/Attendance/ApplyToPayroll?anio=" + ayer.getYear() + "&mes=" + ayer.getMonthValue();
        call(post(url), siso, null).andExpect(status().isForbidden());
        call(post(url), rrhh, null).andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void alRetirarAlTrabajadorSeBorraSuRostro() throws Exception {
        call(put("/AutomineApi/Employee/" + empleadoId + "/Deactivate"), rrhh,
                Map.of("fechaRetiro", LocalDate.now().toString(), "motivo", "Prueba")).andExpect(status().isOk());
        JsonNode p = read(call(get("/AutomineApi/Faces"), siso, null).andExpect(status().isOk()));
        assertThat(p.findValuesAsText("empleadoId")).doesNotContain(String.valueOf(empleadoId));
    }

    // ------------------------------ utilidades ------------------------------

    private MockMultipartHttpServletRequestBuilder rostro(String token, boolean autoriza) {
        MockMultipartHttpServletRequestBuilder req = multipart("/AutomineApi/Employee/" + empleadoId + "/Face");
        req.file(new MockMultipartFile("foto", "rostro.jpg", "image/jpeg", new byte[] { 1, 2, 3 }));
        req.param("plantilla", PLANTILLA).param("autoriza", String.valueOf(autoriza));
        req.header("Authorization", "Bearer " + token);
        return req;
    }

    private MockMultipartHttpServletRequestBuilder marca(String token, String tipo, LocalDateTime cuando, String similitud,
            String dispositivo, boolean foto, String obs) {
        MockMultipartHttpServletRequestBuilder req = multipart("/AutomineApi/Attendance/Mark");
        if (foto) {
            req.file(new MockMultipartFile("foto", "m.jpg", "image/jpeg", new byte[] { 9, 9 }));
        }
        req.param("empleadoId", String.valueOf(empleadoId)).param("tipo", tipo).param("fechaHora", cuando.toString())
                .param("dispositivoId", dispositivo);
        if (similitud != null) {
            req.param("similitud", similitud);
        }
        if (obs != null) {
            req.param("observacion", obs);
        }
        req.header("Authorization", "Bearer " + token);
        return req;
    }

    private String primerIngreso(String email) throws Exception {
        String t = login(email, PASS);
        call(post("/AutomineApi/ChangePassword"), t, Map.of("passwordActual", PASS, "password", NUEVA))
                .andExpect(status().isOk());
        return t;
    }

    private String login(String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password));
        return read(mvc.perform(post("/AutomineApi/Login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())).get("token").asText();
    }

    private void crearUsuario(String email, String rol) throws Exception {
        call(post("/AutomineApi/CreateUser"), admin, Map.of("nombre", rol + " asistencia", "email", email, "password", PASS,
                "rol", rol)).andExpect(status().isCreated());
    }

    private long crearEmpleado(String cedula, String nombres, String apellidos) throws Exception {
        var m = Map.of("cedula", cedula, "nombres", nombres, "apellidos", apellidos, "cargo", "Minero", "salario",
                2_000_000, "fechaIngreso", "2024-01-15", "tipoContrato", "INDEFINIDO", "banco", "Bancolombia",
                "tipoCuenta", "AHORROS", "numeroCuenta", "123456789");
        return read(call(post("/AutomineApi/Employee"), admin, m).andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(req);
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
