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
import java.time.YearMonth;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.automine.incapacidades.CalculadoraIncapacidad;
import com.automine.incapacidades.CalculadoraIncapacidad.Tramo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Incapacidades: el trabajador las registra con su certificado, SG-SST las revisa
 * y las aprobadas se reflejan en la nomina del mes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IncapacidadesTest {

    private static final String PASS = "Automine2026*";
    private static final String NUEVA = "MiClavePropia2026";
    private static final BigDecimal MINIMO = new BigDecimal("1423500");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    String admin;
    String sst;
    String contador;
    String trabajador;
    long empleadoId;
    long otroEmpleadoId;
    long incapacidadId;
    long nominaId;
    /** Mes anterior: no lo usan las demas pruebas, asi el periodo esta abierto. */
    final YearMonth mes = YearMonth.now().minusMonths(1);

    @BeforeAll
    void preparar() throws Exception {
        admin = login("admin@automine.co", PASS);
        empleadoId = crearEmpleado("7070707070", "Carla", "Mejia", "carla@automine.co");
        otroEmpleadoId = crearEmpleado("8080808080", "Diego", "Lopez", null);
        crearUsuario("sst@automine.co", "SGSST", null);
        crearUsuario("conta.incap@automine.co", "CONTADOR", null);
        crearUsuario("carla@automine.co", "EMPLEADO", empleadoId);
        sst = primerIngreso("sst@automine.co");
        contador = primerIngreso("conta.incap@automine.co");
        trabajador = primerIngreso("carla@automine.co");
    }

    // ------------------------------ calculo ------------------------------

    @Test
    void enfermedadGeneralSeReconoceAlMinimoDiarioComoPiso() {
        // 2.000.000 / 30 x 66,67 % = 44.447 < minimo diario 47.450 -> se paga el minimo
        var r = CalculadoraIncapacidad.delMes(List.of(new Tramo("ENFERMEDAD_GENERAL", LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 10), 0)), YearMonth.of(2026, 3), new BigDecimal("2000000"), MINIMO);
        assertThat(r.dias()).isEqualTo(10);
        assertThat(r.valor()).isEqualByComparingTo("474500");
    }

    @Test
    void accidenteDeTrabajoAlCienPorCiento() {
        var r = CalculadoraIncapacidad.delMes(List.of(new Tramo("ACCIDENTE_TRABAJO", LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 14), 0)), YearMonth.of(2026, 3), new BigDecimal("3000000"), MINIMO);
        assertThat(r.dias()).isEqualTo(5);
        assertThat(r.valor()).isEqualByComparingTo("500000");
    }

    @Test
    void mesComercialDeTreintaDiasYProrrogaAlCincuentaPorCiento() {
        // Marzo completo (31 dias) cuenta 30; febrero completo tambien cuenta 30
        var marzo = CalculadoraIncapacidad.delMes(List.of(new Tramo("ACCIDENTE_TRABAJO", LocalDate.of(2026, 2, 20),
                LocalDate.of(2026, 4, 10), 0)), YearMonth.of(2026, 3), new BigDecimal("3000000"), MINIMO);
        assertThat(marzo.dias()).isEqualTo(30);
        assertThat(marzo.valor()).isEqualByComparingTo("3000000");
        var febrero = CalculadoraIncapacidad.delMes(List.of(new Tramo("ACCIDENTE_TRABAJO", LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 3, 5), 0)), YearMonth.of(2026, 2), new BigDecimal("3000000"), MINIMO);
        assertThat(febrero.dias()).isEqualTo(30);
        // Prorroga de enfermedad general despues del dia 90: 50 % (6.000.000 / 30 x 50 % = 100.000 diarios)
        var prorroga = CalculadoraIncapacidad.delMes(List.of(new Tramo("ENFERMEDAD_GENERAL", LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 10), 90)), YearMonth.of(2026, 3), new BigDecimal("6000000"), MINIMO);
        assertThat(prorroga.valor()).isEqualByComparingTo("1000000");
    }

    // ------------------------------ flujo ------------------------------

    @Test
    @Order(1)
    void elTrabajadorRegistraLaSuyaConCertificado() throws Exception {
        mvc.perform(registro(trabajador, null, 1, 10, false)).andExpect(status().isBadRequest());
        mvc.perform(registro(trabajador, otroEmpleadoId, 1, 10, true)).andExpect(status().isForbidden());
        JsonNode r = read(mvc.perform(registro(trabajador, null, 1, 10, true)).andExpect(status().isCreated()));
        incapacidadId = r.get("id").asLong();
        assertThat(r.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(r.get("dias").asInt()).isEqualTo(10);
        assertThat(r.get("empleadoId").asLong()).isEqualTo(empleadoId);
        // No se puede registrar otra que se cruce con las mismas fechas
        mvc.perform(registro(trabajador, null, 5, 12, true)).andExpect(status().isConflict());
        // Solo ve las suyas
        call(get("/AutomineApi/Disabilities"), trabajador, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @Order(2)
    void soloSgsstRevisaYElRechazoExigeMotivo() throws Exception {
        String url = "/AutomineApi/Disability/" + incapacidadId + "/Review";
        call(put(url), trabajador, Map.of("aprobar", true)).andExpect(status().isForbidden());
        call(put(url), contador, Map.of("aprobar", true)).andExpect(status().isForbidden());
        call(put(url), sst, Map.of("aprobar", false)).andExpect(status().isBadRequest());
        call(put(url), sst, Map.of("aprobar", false, "motivo", "El certificado no es legible"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADA"));
        // El trabajador sube de nuevo el soporte y vuelve a revision
        MockMultipartHttpServletRequestBuilder req = multipart(HttpMethod.PUT,
                "/AutomineApi/Disability/" + incapacidadId + "/Support");
        req.file(pdf());
        req.header("Authorization", "Bearer " + trabajador);
        mvc.perform(req).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @Order(3)
    void elCertificadoSoloLoVenElTrabajadorYQuienRevisa() throws Exception {
        String url = "/AutomineApi/Disability/" + incapacidadId + "/Support";
        call(get(url), trabajador, null).andExpect(status().isOk());
        call(get(url), sst, null).andExpect(status().isOk());
        call(get(url), contador, null).andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void laAprobadaSeDescuentaDeLosDiasYSePagaEnLaNomina() throws Exception {
        call(put("/AutomineApi/Disability/" + incapacidadId + "/Review"), sst, Map.of("aprobar", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADA"));
        JsonNode n = read(call(post("/AutomineApi/Payroll"), contador, Map.of("empleadoId", empleadoId, "anio",
                mes.getYear(), "mes", mes.getMonthValue())).andExpect(status().isCreated()));
        nominaId = n.get("id").asLong();
        assertThat(n.get("diasTrabajados").asInt()).isEqualTo(20);
        assertThat(n.get("diasIncapacidad").asInt()).isEqualTo(10);
        // Minimo diario 2026: 1.750.905 / 30 = 58.363,5 x 10 dias
        assertThat(n.get("valorIncapacidad").decimalValue()).isEqualByComparingTo("583635");
        // No se pueden poner 30 dias trabajados encima de 10 de incapacidad
        call(put("/AutomineApi/Payroll/Detail/" + nominaId), contador, Map.of("diasTrabajados", 30))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void alAnularlaLaNominaAbiertaSeRecalcula() throws Exception {
        call(put("/AutomineApi/Disability/" + incapacidadId + "/Cancel"), sst, Map.of("motivo", "Registrada por error"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ANULADA"));
        call(get("/AutomineApi/Payroll/Detail/" + nominaId), contador, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.diasTrabajados").value(30))
                .andExpect(jsonPath("$.diasIncapacidad").value(0));
    }

    // ------------------------------ utilidades ------------------------------

    private MockMultipartHttpServletRequestBuilder registro(String token, Long empleado, int desde, int hasta,
            boolean conSoporte) {
        MockMultipartHttpServletRequestBuilder req = multipart("/AutomineApi/Disability");
        if (conSoporte) {
            req.file(pdf());
        }
        req.param("tipo", "ENFERMEDAD_GENERAL")
                .param("fechaInicio", mes.atDay(desde).toString())
                .param("fechaFin", mes.atDay(hasta).toString())
                .param("entidad", "EPS Sura");
        if (empleado != null) {
            req.param("empleadoId", String.valueOf(empleado));
        }
        req.header("Authorization", "Bearer " + token);
        return req;
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("soporte", "certificado.pdf", "application/pdf", "%PDF-1.4 prueba".getBytes());
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

    private void crearUsuario(String email, String rol, Long empleado) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("nombre", rol + " incapacidades", "email", email,
                "password", PASS, "rol", rol));
        if (empleado != null) {
            body.put("empleadoId", empleado);
        }
        call(post("/AutomineApi/CreateUser"), admin, body).andExpect(status().isCreated());
    }

    private long crearEmpleado(String cedula, String nombres, String apellidos, String email) throws Exception {
        var m = new java.util.HashMap<String, Object>(Map.of("cedula", cedula, "nombres", nombres, "apellidos",
                apellidos, "cargo", "Minero", "salario", 2_000_000, "fechaIngreso", "2024-01-15", "tipoContrato",
                "INDEFINIDO", "banco", "Bancolombia", "tipoCuenta", "AHORROS", "numeroCuenta", "123456789"));
        if (email != null) {
            m.put("email", email);
        }
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
