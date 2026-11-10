package com.automine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.automine.dian.NominaElectronicaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Parametros con vigencia (salario minimo por año) y XML de nomina electronica. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NominaElectronicaYVigenciasTest {

    private static final String PASS = "Automine2026*";
    private static final String NUEVA = "MiClavePropia2026";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    String admin;
    String contador;
    long empleadoId;

    @BeforeAll
    void preparar() throws Exception {
        admin = login("admin@automine.co", PASS);
        var m = Map.of("cedula", "8181818181", "nombres", "Julian Andres", "apellidos", "Mora Rios", "cargo", "Minero",
                "salario", 1_423_500, "fechaIngreso", "2024-01-15", "tipoContrato", "INDEFINIDO", "banco", "Bancolombia",
                "tipoCuenta", "AHORROS", "numeroCuenta", "987654321");
        empleadoId = read(call(post("/AutomineApi/Employee"), admin, m).andExpect(status().isCreated())).get("id").asLong();
        call(post("/AutomineApi/CreateUser"), admin, Map.of("nombre", "Contador DIAN", "email", "conta.dian@automine.co",
                "password", PASS, "rol", "CONTADOR")).andExpect(status().isCreated());
        String t = login("conta.dian@automine.co", PASS);
        call(post("/AutomineApi/ChangePassword"), t, Map.of("passwordActual", PASS, "password", NUEVA)).andExpect(status().isOk());
        contador = t;
    }

    @Test
    void cadaNominaUsaLosValoresVigentesDeSuMes() throws Exception {
        JsonNode v = read(call(get("/AutomineApi/Settings/Validity"), contador, null).andExpect(status().isOk()));
        assertThat(v.toString()).contains("1750905");
        JsonNode n2025 = read(call(post("/AutomineApi/Payroll"), contador, Map.of("empleadoId", empleadoId, "anio", 2025, "mes", 3))
                .andExpect(status().isCreated()));
        assertThat(n2025.get("auxilioTransporte").decimalValue()).isEqualByComparingTo("200000");
        JsonNode n2026 = read(call(post("/AutomineApi/Payroll"), contador, Map.of("empleadoId", empleadoId, "anio", 2026, "mes", 3))
                .andExpect(status().isCreated()));
        assertThat(n2026.get("auxilioTransporte").decimalValue()).isEqualByComparingTo("249095");
        call(post("/AutomineApi/Settings/Validity"), contador, Map.of("clave", "SALARIO_MINIMO", "desde", "2027-01-01", "valor", "1900000"))
                .andExpect(status().isForbidden());
        call(post("/AutomineApi/Settings/Validity"), admin, Map.of("clave", "SALARIO_MINIMO", "desde", "2027-01-01", "valor", "1900000",
                "norma", "Prueba")).andExpect(status().isCreated());
    }

    @Test
    void xmlDeNominaElectronicaConEstructuraDian() throws Exception {
        JsonNode n = read(call(post("/AutomineApi/Payroll"), contador, Map.of("empleadoId", empleadoId, "anio", 2025,
                "mes", 11, "horasExtraDiurnas", 6)).andExpect(status().isCreated()));
        long id = n.get("id").asLong();
        // Pendiente de aprobacion: no se genera
        mvc.perform(get("/AutomineApi/Payroll/Detail/" + id + "/Dian").header("Authorization", "Bearer " + contador))
                .andExpect(status().isBadRequest());
        call(put("/AutomineApi/Payroll/Period/" + n.get("periodoId").asLong() + "/Approve"), contador, null).andExpect(status().isOk());
        byte[] xml = mvc.perform(get("/AutomineApi/Payroll/Detail/" + id + "/Dian").header("Authorization", "Bearer " + contador))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        var f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("NominaIndividual");
        Element info = (Element) doc.getElementsByTagName("InformacionGeneral").item(0);
        assertThat(info.getAttribute("CUNE")).hasSize(96);
        assertThat(info.getAttribute("TipoXML")).isEqualTo("102");
        assertThat(doc.getElementsByTagName("HED").getLength()).isEqualTo(1);
        String total = doc.getElementsByTagName("ComprobanteTotal").item(0).getTextContent();
        assertThat(new BigDecimal(total)).isEqualByComparingTo(n.get("netoPagar").decimalValue());
        assertThat(new String(xml, StandardCharsets.UTF_8)).contains("NumeroDocumento=\"8181818181\"", "PrimerNombre=\"Julian\"");
        mvc.perform(get("/AutomineApi/Payroll/Period/" + n.get("periodoId").asLong() + "/Dian").header("Authorization", "Bearer " + contador))
                .andExpect(status().isOk());
    }

    @Test
    void digitoDeVerificacionDelNit() {
        assertThat(NominaElectronicaService.nit("900373913")[1]).isEqualTo("4");
        assertThat(NominaElectronicaService.nit("900.123.456-7")).containsExactly("900123456", "7");
    }

    // ------------------------------ utilidades ------------------------------

    private String login(String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password));
        return read(mvc.perform(post("/AutomineApi/Login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())).get("token").asText();
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
