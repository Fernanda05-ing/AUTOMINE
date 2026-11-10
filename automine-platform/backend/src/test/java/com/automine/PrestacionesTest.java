package com.automine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.automine.prestaciones.CalculadoraPrestaciones;
import com.automine.prestaciones.Festivos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Vacaciones, prima, cesantias y liquidacion de retiro. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrestacionesTest {

    private static final String PASS = "Automine2026*";
    private static final String NUEVA = "MiClavePropia2026";
    private static final BigDecimal MINIMO = new BigDecimal("1423500");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    String admin;
    String rrhh;
    String contador;
    String trabajador;
    long empleadoId;
    long retiroId;
    long vacacionId;
    LocalDate inicioVac;
    /** Dentro de dos meses: periodo de nomina abierto y sin otras pruebas. */
    final YearMonth mes = YearMonth.now().plusMonths(2);

    @BeforeAll
    void preparar() throws Exception {
        admin = login("admin@automine.co", PASS);
        empleadoId = crearEmpleado("5151515151", "Elena", "Rios", "elena@automine.co");
        retiroId = crearEmpleado("5252525252", "Fabio", "Suarez", null);
        crearUsuario("th.prest@automine.co", "RRHH", null);
        crearUsuario("conta.prest@automine.co", "CONTADOR", null);
        crearUsuario("elena@automine.co", "EMPLEADO", empleadoId);
        rrhh = primerIngreso("th.prest@automine.co");
        contador = primerIngreso("conta.prest@automine.co");
        trabajador = primerIngreso("elena@automine.co");
        inicioVac = mes.atDay(3);
        while (!Festivos.esHabil(inicioVac, true)) {
            inicioVac = inicioVac.plusDays(1);
        }
    }

    // ------------------------------ reglas puras ------------------------------

    @Test
    void festivosDeColombia2026() {
        assertThat(Festivos.esFestivo(LocalDate.of(2026, 1, 12))).isTrue(); // Reyes, trasladado al lunes
        assertThat(Festivos.esFestivo(LocalDate.of(2026, 4, 2))).isTrue(); // Jueves Santo
        assertThat(Festivos.esFestivo(LocalDate.of(2026, 4, 3))).isTrue(); // Viernes Santo
        assertThat(Festivos.esFestivo(LocalDate.of(2026, 3, 23))).isTrue(); // San Jose, trasladado al lunes
        assertThat(Festivos.esFestivo(LocalDate.of(2026, 7, 20))).isTrue();
        assertThat(Festivos.esFestivo(LocalDate.of(2026, 7, 21))).isFalse();
        // 3 habiles desde el viernes 20 de marzo: 20, 21 (sabado), 24 (22 domingo y 23 festivo)
        assertThat(Festivos.finVacaciones(LocalDate.of(2026, 3, 20), 3, true)).isEqualTo(LocalDate.of(2026, 3, 24));
    }

    @Test
    void diasComercialesYPrestaciones() {
        assertThat(CalculadoraPrestaciones.dias360(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).isEqualTo(360);
        assertThat(CalculadoraPrestaciones.dias360(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))).isEqualTo(180);
        assertThat(CalculadoraPrestaciones.dias360(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))).isEqualTo(30);
        BigDecimal base = CalculadoraPrestaciones.base(new BigDecimal("2000000"), new BigDecimal("200000"), MINIMO);
        assertThat(base).isEqualByComparingTo("2200000");
        assertThat(CalculadoraPrestaciones.prima(base, 180)).isEqualByComparingTo("1100000");
        BigDecimal ces = CalculadoraPrestaciones.cesantias(base, 360);
        assertThat(CalculadoraPrestaciones.intereses(ces, 360)).isEqualByComparingTo("264000");
        assertThat(CalculadoraPrestaciones.diasVacacionesCausados(360)).isEqualByComparingTo("15");
        // Indefinido, 2 años, menos de 10 SMMLV: 30 + 20 = 50 dias de salario
        assertThat(CalculadoraPrestaciones.indemnizacion("INDEFINIDO", new BigDecimal("3000000"), MINIMO,
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 30), null)).isEqualByComparingTo("5000000");
        assertThat(CalculadoraPrestaciones.aplica("PRESTACION_SERVICIOS")).isFalse();
    }

    // ------------------------------ vacaciones ------------------------------

    @Test
    @Order(1)
    void elTrabajadorSolicitaVacacionesSegunSuSaldo() throws Exception {
        JsonNode saldo = read(call(get("/AutomineApi/Vacations/Balance/" + empleadoId), trabajador, null)
                .andExpect(status().isOk()));
        assertThat(saldo.get("diasCausados").decimalValue()).isGreaterThan(BigDecimal.valueOf(30));
        call(post("/AutomineApi/Vacation"), trabajador, Map.of("fechaInicio", inicioVac.toString(), "diasHabiles", 59))
                .andExpect(status().isBadRequest());
        JsonNode v = read(call(post("/AutomineApi/Vacation"), trabajador,
                Map.of("fechaInicio", inicioVac.toString(), "diasHabiles", 5)).andExpect(status().isCreated()));
        vacacionId = v.get("id").asLong();
        assertThat(v.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(LocalDate.parse(v.get("fechaRegreso").asText())).isAfter(LocalDate.parse(v.get("fechaFin").asText()));
        call(post("/AutomineApi/Vacation"), trabajador, Map.of("fechaInicio", inicioVac.toString(), "diasHabiles", 2))
                .andExpect(status().isConflict());
        call(put("/AutomineApi/Vacation/" + vacacionId + "/Review"), trabajador, Map.of("aprobar", true))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    void lasVacacionesAprobadasSePaganEnLaNomina() throws Exception {
        JsonNode v = read(call(put("/AutomineApi/Vacation/" + vacacionId + "/Review"), rrhh, Map.of("aprobar", true))
                .andExpect(status().isOk()));
        LocalDate fin = LocalDate.parse(v.get("fechaFin").asText());
        int calendario = (int) java.time.temporal.ChronoUnit.DAYS.between(inicioVac, fin) + 1;
        JsonNode n = read(call(post("/AutomineApi/Payroll"), contador, Map.of("empleadoId", empleadoId, "anio",
                mes.getYear(), "mes", mes.getMonthValue())).andExpect(status().isCreated()));
        assertThat(n.get("diasVacaciones").asInt()).isEqualTo(calendario);
        assertThat(n.get("diasTrabajados").asInt()).isEqualTo(30 - calendario);
        BigDecimal esperado = new BigDecimal("2000000").multiply(BigDecimal.valueOf(calendario))
                .divide(BigDecimal.valueOf(30), 0, RoundingMode.HALF_UP);
        assertThat(n.get("valorVacaciones").decimalValue()).isEqualByComparingTo(esperado);
    }

    // ------------------------------ prima y cesantias ------------------------------

    @Test
    @Order(3)
    void primaDelSemestreSeLiquidaUnaSolaVez() throws Exception {
        String url = "/AutomineApi/Benefits?tipo=PRIMA&anio=2026&periodo=1";
        JsonNode p = read(call(get(url), contador, null).andExpect(status().isOk()));
        JsonNode elena = null;
        for (JsonNode d : p.get("detalle")) {
            if (d.get("empleadoId").asLong() == empleadoId) {
                elena = d;
            }
        }
        assertThat(elena).isNotNull();
        assertThat(elena.get("dias").asInt()).isEqualTo(180);
        assertThat(elena.get("valor").decimalValue()).isEqualByComparingTo("1100000");
        call(post("/AutomineApi/Benefits/Settle"), trabajador, Map.of("tipo", "PRIMA", "anio", 2026, "periodo", 1))
                .andExpect(status().isForbidden());
        call(post("/AutomineApi/Benefits/Settle"), contador, Map.of("tipo", "PRIMA", "anio", 2026, "periodo", 1))
                .andExpect(status().isOk());
        call(post("/AutomineApi/Benefits/Settle"), contador, Map.of("tipo", "PRIMA", "anio", 2026, "periodo", 1))
                .andExpect(status().isBadRequest());
        call(get("/AutomineApi/Benefits/Employee/" + empleadoId), trabajador, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("PRIMA"));
    }

    // ------------------------------ liquidacion de retiro ------------------------------

    @Test
    @Order(4)
    void liquidacionFinalConIndemnizacionYRetiro() throws Exception {
        Map<String, Object> body = Map.of("fechaRetiro", LocalDate.now().toString(), "motivo", "SIN_JUSTA_CAUSA");
        String base = "/AutomineApi/Employee/" + retiroId + "/Settlement";
        JsonNode s = read(call(post(base + "/Preview"), rrhh, body).andExpect(status().isOk()));
        BigDecimal suma = BigDecimal.ZERO;
        for (String k : new String[] { "salarioPendiente", "prima", "cesantias", "interesesCesantias", "vacaciones",
                "indemnizacion" }) {
            suma = suma.add(s.get(k).decimalValue());
        }
        assertThat(s.get("indemnizacion").decimalValue()).isPositive();
        assertThat(s.get("total").decimalValue()).isEqualByComparingTo(suma);
        call(post(base), contador, body).andExpect(status().isForbidden());
        JsonNode l = read(call(post(base), rrhh, body).andExpect(status().isCreated()));
        call(get("/AutomineApi/Employee/" + retiroId), rrhh, null).andExpect(jsonPath("$.estado").value("INACTIVO"));
        call(post(base), rrhh, body).andExpect(status().isBadRequest());
        mvc.perform(get("/AutomineApi/Settlement/" + l.get("id").asLong() + "/Pdf").header("Authorization",
                "Bearer " + rrhh)).andExpect(status().isOk());
    }

    // ------------------------------ utilidades ------------------------------

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
        var body = new java.util.HashMap<String, Object>(Map.of("nombre", rol + " prestaciones", "email", email,
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
