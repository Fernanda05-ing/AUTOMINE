package com.automine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Examenes medicos, indicadores del SG-SST e inspecciones preoperacionales. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SaludInspeccionesTest {

    private static final String PASS = "Automine2026*";
    private static final String NUEVA = "MiClavePropia2026";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    String admin;
    String siso;
    String contador;
    long empleadoId;
    long sinExamenId;

    @BeforeAll
    void preparar() throws Exception {
        admin = login("admin@automine.co", PASS);
        empleadoId = crearEmpleado("7171717171", "Hugo", "Vargas");
        sinExamenId = crearEmpleado("7272727272", "Ines", "Zapata");
        crearUsuario("siso.salud@automine.co", "SGSST");
        crearUsuario("conta.salud@automine.co", "CONTADOR");
        siso = primerIngreso("siso.salud@automine.co");
        contador = primerIngreso("conta.salud@automine.co");
    }

    @Test
    void examenesConVencimientoYEstado() throws Exception {
        String hace = LocalDate.now().minusDays(10).toString();
        mvc.perform(examen(siso, "APTO_CON_RESTRICCIONES", null, hace)).andExpect(status().isBadRequest());
        mvc.perform(examen(contador, "APTO", null, hace)).andExpect(status().isForbidden());
        JsonNode x = read(mvc.perform(examen(siso, "APTO_CON_RESTRICCIONES", "No levantar más de 15 kg", hace))
                .andExpect(status().isCreated()));
        assertThat(x.get("proximaFecha").asText()).isEqualTo(LocalDate.now().minusDays(10).plusYears(1).toString());
        assertThat(x.get("vigencia").asText()).isEqualTo("VIGENTE");
        JsonNode estado = read(call(get("/AutomineApi/MedicalExams/Status"), siso, null).andExpect(status().isOk()));
        String hugo = null, ines = null;
        for (JsonNode e : estado) {
            if (e.get("empleadoId").asLong() == empleadoId) {
                hugo = e.get("estado").asText();
            }
            if (e.get("empleadoId").asLong() == sinExamenId) {
                ines = e.get("estado").asText();
            }
        }
        assertThat(hugo).isEqualTo("VIGENTE");
        assertThat(ines).isEqualTo("SIN_EXAMEN");
    }

    @Test
    void indicadoresDeAccidentalidadYAusentismo() throws Exception {
        call(post("/AutomineApi/Accident"), siso, Map.of("tipo", "ACCIDENTE", "empleadoId", empleadoId,
                "fecha", LocalDateTime.now().minusHours(2).withNano(0).toString(), "lugar", "Frente 2",
                "descripcion", "Golpe en la mano con herramienta", "gravedad", "LEVE", "diasIncapacidad", 3))
                .andExpect(status().isCreated());
        JsonNode r = read(call(get("/AutomineApi/Sst/Indicators?anio=" + LocalDate.now().getYear()), siso, null)
                .andExpect(status().isOk()));
        JsonNode mes = r.get("meses").get(LocalDate.now().getMonthValue() - 1);
        assertThat(mes.get("accidentes").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(mes.get("frecuencia").decimalValue()).isPositive();
        assertThat(mes.get("severidad").decimalValue()).isPositive();
        call(get("/AutomineApi/Sst/Indicators?anio=2026"), contador, null).andExpect(status().isForbidden());
    }

    @Test
    void inspeccionPreoperacionalConFallaCritica() throws Exception {
        JsonNode listas = read(call(get("/AutomineApi/Inspections/Checklists"), siso, null).andExpect(status().isOk()));
        JsonNode vehiculo = listas.get("VEHICULO");
        JsonNode ok = read(mvc.perform(inspeccion(vehiculo, null, false)).andExpect(status().isCreated()));
        assertThat(ok.get("resultado").asText()).isEqualTo("APTO");
        String critico = null;
        for (JsonNode p : vehiculo) {
            if (p.get("critico").asBoolean()) {
                critico = p.get("item").asText();
                break;
            }
        }
        // Falla sin foto: no se acepta
        mvc.perform(inspeccion(vehiculo, critico, false)).andExpect(status().isBadRequest());
        JsonNode mal = read(mvc.perform(inspeccion(vehiculo, critico, true)).andExpect(status().isCreated()));
        assertThat(mal.get("resultado").asText()).isEqualTo("NO_APTO");
        assertThat(mal.get("fotos").size()).isEqualTo(1);
        call(get("/AutomineApi/Inspections"), siso, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ------------------------------ utilidades ------------------------------

    private MockMultipartHttpServletRequestBuilder examen(String token, String concepto, String restricciones, String fecha) {
        MockMultipartHttpServletRequestBuilder req = multipart("/AutomineApi/MedicalExam");
        req.param("empleadoId", String.valueOf(empleadoId)).param("tipo", "INGRESO").param("fecha", fecha)
                .param("concepto", concepto).param("ips", "IPS Salud Minera");
        if (restricciones != null) {
            req.param("restricciones", restricciones);
        }
        req.header("Authorization", "Bearer " + token);
        return req;
    }

    private MockMultipartHttpServletRequestBuilder inspeccion(JsonNode lista, String falla, boolean foto) throws Exception {
        List<Map<String, String>> items = new ArrayList<>();
        for (JsonNode p : lista) {
            String item = p.get("item").asText();
            items.add(item.equals(falla) ? Map.of("item", item, "estado", "FALLA", "observacion", "No funciona")
                    : Map.of("item", item, "estado", "OK"));
        }
        MockMultipartHttpServletRequestBuilder req = multipart("/AutomineApi/Inspection");
        req.param("equipo", "Camioneta ABC-123").param("tipo", "VEHICULO").param("items", json.writeValueAsString(items));
        if (foto) {
            req.file(new MockMultipartFile("fotos", "falla.jpg", "image/jpeg", new byte[] { 1, 2, 3 }));
        }
        req.header("Authorization", "Bearer " + siso);
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
        call(post("/AutomineApi/CreateUser"), admin, Map.of("nombre", rol + " salud", "email", email, "password", PASS,
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
