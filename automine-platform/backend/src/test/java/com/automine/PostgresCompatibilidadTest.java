package com.automine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * La misma API sobre PostgreSQL real (como Supabase): migraciones de db/migration/postgresql,
 * columnas bytea para archivos y consultas con filtros opcionales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostgresCompatibilidadTest {

    private static final String PASS = "Automine2026*";
    private static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry r) throws Exception {
        pg = EmbeddedPostgres.builder().start();
        r.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.flyway.locations", () -> "classpath:db/migration/postgresql");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @AfterAll
    static void detener() throws Exception {
        if (pg != null) {
            pg.close();
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void flujosPrincipalesSobrePostgres() throws Exception {
        String admin = read(mvc.perform(post("/AutomineApi/Login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "admin@automine.co", "password", PASS))))
                .andExpect(status().isOk())).get("token").asText();

        // Empleado y busquedas con filtros opcionales (parametros nulos)
        long emp = read(call(post("/AutomineApi/Employee"), admin, Map.of("cedula", "9191919191", "nombres", "Pedro", "apellidos",
                "Gomez", "cargo", "Minero", "salario", 2_000_000, "fechaIngreso", "2024-01-15", "tipoContrato", "INDEFINIDO",
                "banco", "Bancolombia", "tipoCuenta", "AHORROS", "numeroCuenta", "111")).andExpect(status().isCreated())).get("id").asLong();
        call(get("/AutomineApi/Employees"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Employees?q=ped"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Inventory"), admin, null).andExpect(status().isOk());

        // Incapacidad con certificado (bytea) y descarga
        YearMonth mes = YearMonth.now().minusMonths(1);
        var inc = multipart("/AutomineApi/Disability");
        inc.file(new MockMultipartFile("soporte", "c.pdf", "application/pdf", "%PDF-1.4".getBytes()));
        inc.param("empleadoId", String.valueOf(emp)).param("tipo", "ENFERMEDAD_GENERAL")
                .param("fechaInicio", mes.atDay(2).toString()).param("fechaFin", mes.atDay(4).toString());
        inc.header("Authorization", "Bearer " + admin);
        long incId = read(mvc.perform(inc).andExpect(status().isCreated())).get("id").asLong();
        byte[] pdf = mvc.perform(get("/AutomineApi/Disability/" + incId + "/Support").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf)).isEqualTo("%PDF-1.4");
        call(get("/AutomineApi/Disabilities"), admin, null).andExpect(status().isOk());
        call(put("/AutomineApi/Disability/" + incId + "/Review"), admin, Map.of("aprobar", true)).andExpect(status().isOk());

        // Nomina con incapacidad
        JsonNode n = read(call(post("/AutomineApi/Payroll"), admin, Map.of("empleadoId", emp, "anio", mes.getYear(), "mes",
                mes.getMonthValue())).andExpect(status().isCreated()));
        assertThat(n.get("diasIncapacidad").asInt()).isEqualTo(3);
        call(get("/AutomineApi/Payroll/Periods"), admin, null).andExpect(status().isOk());

        // Vacaciones y prestaciones
        call(get("/AutomineApi/Vacations/Balance/" + emp), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Vacations"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Benefits?tipo=PRIMA&anio=2026&periodo=1"), admin, null).andExpect(status().isOk());

        // Asistencia con foto
        var marca = multipart("/AutomineApi/Attendance/Mark");
        marca.file(new MockMultipartFile("foto", "m.jpg", "image/jpeg", new byte[] { 1, 2, 3 }));
        marca.param("empleadoId", String.valueOf(emp)).param("tipo", "ENTRADA").param("similitud", "0.9")
                .param("fechaHora", LocalDate.now().atTime(6, 0).toString());
        marca.header("Authorization", "Bearer " + admin);
        long marcaId = read(mvc.perform(marca).andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(get("/AutomineApi/Attendance/" + marcaId + "/Photo").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        call(get("/AutomineApi/Attendance?fecha=" + LocalDate.now()), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Attendance/Summary?anio=" + LocalDate.now().getYear() + "&mes=" + LocalDate.now().getMonthValue()),
                admin, null).andExpect(status().isOk());

        // SG-SST, inspecciones, indicadores, chat, tablero y reportes
        call(get("/AutomineApi/MedicalExams/Status"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Inspections"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Sst/Indicators?anio=" + LocalDate.now().getYear()), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Accidents"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Chat/Conversations"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Dashboard"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Reports/mensual/pdf"), admin, null).andExpect(status().isOk());
        call(get("/AutomineApi/Settings/Validity"), admin, null).andExpect(status().isOk());
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
