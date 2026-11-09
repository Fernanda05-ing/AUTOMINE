package com.automine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pruebas de extremo a extremo sobre la API (H2 en modo MySQL, esquema creado por Flyway):
 * autenticacion, permisos por rol y reglas de negocio RN001 a RN006.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReglasNegocioTest {

    private static final String PASS = "Automine2026*";
    private static final String NUEVA = "MiClavePropia2026";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    String admin;
    String rrhh;
    String contador;
    String empleado;
    long empleadoId;
    long otroEmpleadoId;
    long periodoId;
    long nominaId;

    @BeforeAll
    void preparar() throws Exception {
        admin = login("admin@automine.co", PASS);
        empleadoId = crearEmpleado(admin, "1010101010", "Ana", "Perez", "ana@automine.co").get("id").asLong();
        otroEmpleadoId = crearEmpleado(admin, "2020202020", "Beto", "Rojas", null).get("id").asLong();
        crearUsuario("rrhh@automine.co", "RRHH", null);
        crearUsuario("contador@automine.co", "CONTADOR", null);
        crearUsuario("ana@automine.co", "EMPLEADO", empleadoId);
        rrhh = primerIngreso("rrhh@automine.co");
        contador = primerIngreso("contador@automine.co");
        empleado = primerIngreso("ana@automine.co");
    }

    @Test
    @Order(1)
    void loginInvalidoYSinToken() throws Exception {
        mvc.perform(post("/AutomineApi/Login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@automine.co\",\"password\":\"incorrecta1\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/AutomineApi/Employees")).andExpect(status().isUnauthorized());
        mvc.perform(get("/AutomineApi/Health")).andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void rn003CedulaUnica() throws Exception {
        call(post("/AutomineApi/Employee"), rrhh, empleadoBody("1010101010", "Otra", "Persona", null))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    void rn004SoloRrhhCreaEmpleados() throws Exception {
        call(post("/AutomineApi/Employee"), contador, empleadoBody("3030303030", "No", "Permitido", null))
                .andExpect(status().isForbidden());
        call(post("/AutomineApi/Employee"), rrhh, empleadoBody("3030303030", "Si", "Permitido", null))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(4)
    void rn001SinDobleNominaEnElPeriodo() throws Exception {
        LocalDate hoy = LocalDate.now();
        Map<String, Object> body = Map.of("empleadoId", empleadoId, "anio", hoy.getYear(), "mes", hoy.getMonthValue(),
                "horasExtraDiurnas", 4);
        JsonNode n = read(call(post("/AutomineApi/Payroll"), contador, body).andExpect(status().isCreated()));
        nominaId = n.get("id").asLong();
        periodoId = n.get("periodoId").asLong();
        assertThat(n.get("netoPagar").decimalValue()).isPositive();
        call(post("/AutomineApi/Payroll"), contador, body).andExpect(status().isConflict());
    }

    @Test
    @Order(5)
    void rn002NoSeConsignaSinNominaAprobada() throws Exception {
        Map<String, Object> t = Map.of("nominaId", nominaId, "fechaPago", LocalDate.now().toString(),
                "referencia", "REF-0001");
        call(post("/AutomineApi/Transfer"), contador, t).andExpect(status().isBadRequest());
        call(post("/AutomineApi/Transfer"), contador, Map.of("nominaId", 99999, "fechaPago",
                LocalDate.now().toString(), "referencia", "REF-0002")).andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void rn005SoloContabilidadApruebaPagos() throws Exception {
        call(put("/AutomineApi/Payroll/Period/" + periodoId + "/Approve"), admin, null)
                .andExpect(status().isForbidden());
        call(put("/AutomineApi/Payroll/Period/" + periodoId + "/Approve"), contador, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADO"));

        JsonNode c = read(call(post("/AutomineApi/Transfer"), contador, Map.of("nominaId", nominaId, "fechaPago",
                LocalDate.now().toString(), "referencia", "REF-0001")).andExpect(status().isCreated()));
        long id = c.get("id").asLong();
        call(put("/AutomineApi/Transfer/" + id + "/Approve"), admin, null).andExpect(status().isForbidden());
        call(put("/AutomineApi/Transfer/" + id + "/Approve"), contador, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));
        call(get("/AutomineApi/Payroll/Detail/" + nominaId), contador, null)
                .andExpect(jsonPath("$.estado").value("PAGADA"));
    }

    @Test
    @Order(7)
    void empleadoSoloVeSuInformacion() throws Exception {
        call(get("/AutomineApi/Payroll/" + empleadoId), empleado, null).andExpect(status().isOk());
        call(get("/AutomineApi/Transfers/" + empleadoId), empleado, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("APROBADA"));
        call(get("/AutomineApi/Payroll/" + otroEmpleadoId), empleado, null).andExpect(status().isForbidden());
        call(get("/AutomineApi/Employees"), empleado, null).andExpect(status().isForbidden());
        call(get("/AutomineApi/Payroll/Detail/" + nominaId + "/Payslip"), empleado, null).andExpect(status().isOk());
    }

    @Test
    @Order(8)
    void rn006CertificadosYValidacionPublica() throws Exception {
        String pdf = call(get("/AutomineApi/Certificate/" + empleadoId), empleado, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentType();
        assertThat(pdf).isEqualTo("application/pdf");
        JsonNode lista = read(call(get("/AutomineApi/Certificates"), empleado, null));
        String codigo = lista.get(0).get("codigoValidacion").asText();
        mvc.perform(get("/AutomineApi/Certificate/Validate/" + codigo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true));

        // RN006: un empleado retirado con historial valido si puede recibir certificado
        call(put("/AutomineApi/Employee/" + otroEmpleadoId + "/Deactivate"), rrhh,
                Map.of("fechaRetiro", LocalDate.now().toString(), "motivo", "Renuncia voluntaria"))
                .andExpect(status().isOk());
        call(post("/AutomineApi/Certificate"), rrhh, Map.of("empleadoId", otroEmpleadoId))
                .andExpect(status().isCreated());
        call(post("/AutomineApi/Payroll"), contador, Map.of("empleadoId", otroEmpleadoId, "anio", 2030, "mes", 1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(9)
    void logoutRevocaElToken() throws Exception {
        String t = login("rrhh@automine.co", NUEVA);
        call(post("/AutomineApi/Logout"), t, null).andExpect(status().isOk());
        call(get("/AutomineApi/Profile"), t, null).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(10)
    void reportesYDashboard() throws Exception {
        call(get("/AutomineApi/Dashboard"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Reports/mensual/pdf"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Reports/empleados/excel"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Reports/contabilidad/pdf"), empleado, null).andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    void contrasenaTemporalBloqueaHastaCambiarla() throws Exception {
        crearUsuario("nuevo@automine.co", "ALMACENISTA", null);
        String t = login("nuevo@automine.co", PASS);
        call(get("/AutomineApi/Inventory"), t, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CAMBIO_PASSWORD"));
        call(get("/AutomineApi/Profile"), t, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarPassword").value(true));
        call(post("/AutomineApi/ChangePassword"), t, Map.of("passwordActual", PASS, "password", NUEVA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.debeCambiarPassword").value(false));
        call(get("/AutomineApi/Inventory"), t, null).andExpect(status().isOk());
    }

    // ------------------------------ utilidades ------------------------------

    /** Ingresa con la clave temporal y la cambia, como haria un usuario nuevo. */
    private String primerIngreso(String email) throws Exception {
        String t = login(email, PASS);
        call(post("/AutomineApi/ChangePassword"), t, Map.of("passwordActual", PASS, "password", NUEVA))
                .andExpect(status().isOk());
        return t;
    }

    private String login(String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password));
        JsonNode r = read(mvc.perform(post("/AutomineApi/Login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()));
        return r.get("token").asText();
    }

    private void crearUsuario(String email, String rol, Long empleado) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("nombre", rol + " prueba", "email", email,
                "password", PASS, "rol", rol));
        if (empleado != null) {
            body.put("empleadoId", empleado);
        }
        call(post("/AutomineApi/CreateUser"), admin, body).andExpect(status().isCreated());
    }

    private JsonNode crearEmpleado(String token, String cedula, String nombres, String apellidos, String email)
            throws Exception {
        return read(call(post("/AutomineApi/Employee"), token, empleadoBody(cedula, nombres, apellidos, email))
                .andExpect(status().isCreated()));
    }

    private Map<String, Object> empleadoBody(String cedula, String nombres, String apellidos, String email) {
        var m = new java.util.HashMap<String, Object>(Map.of("cedula", cedula, "nombres", nombres, "apellidos",
                apellidos, "cargo", "Minero", "salario", 2_000_000, "fechaIngreso", "2024-01-15", "tipoContrato",
                "INDEFINIDO", "banco", "Bancolombia", "tipoCuenta", "AHORROS", "numeroCuenta", "123456789"));
        if (email != null) {
            m.put("email", email);
        }
        return m;
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
