package com.automine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.automine.common.MailService;
import com.automine.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/** Envio de correo por la API de Brevo (servidor simulado). */
class MailBrevoTest {

    @Test
    void enviaPorApiYReportaErrores() throws Exception {
        ObjectMapper json = new ObjectMapper();
        AtomicReference<String> cuerpo = new AtomicReference<>();
        AtomicReference<String> llave = new AtomicReference<>();
        AtomicInteger codigo = new AtomicInteger(201);
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/v3/smtp/email", ex -> {
            cuerpo.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            llave.set(ex.getRequestHeaders().getFirst("api-key"));
            ex.sendResponseHeaders(codigo.get(), -1);
            ex.close();
        });
        srv.start();
        try {
            AppProperties props = new AppProperties("demo", null, "https://demo.test", null, null, "autominesas@gmail.com",
                    new AppProperties.Company("AUTOMINE S.A.S.", "900123456-7", "Medellin", "A", "B"), null);
            MailService mail = new MailService(null, props, json);
            ReflectionTestUtils.setField(mail, "brevoKey", "llave-prueba");
            ReflectionTestUtils.setField(mail, "brevoUrl", "http://127.0.0.1:" + srv.getAddress().getPort() + "/v3/smtp/email");
            assertThat(mail.configurado()).isTrue();

            mail.enviarAhora("ana@correo.co", MailService.Correo.de("Bienvenida", "Hola", "Entrar", "https://demo.test", "Su clave temporal"));
            JsonNode b = json.readTree(cuerpo.get());
            assertThat(llave.get()).isEqualTo("llave-prueba");
            assertThat(b.at("/sender/email").asText()).isEqualTo("autominesas@gmail.com");
            assertThat(b.at("/to/0/email").asText()).isEqualTo("ana@correo.co");
            assertThat(b.get("subject").asText()).isEqualTo("Bienvenida");
            assertThat(b.get("htmlContent").asText()).contains("Su clave temporal");

            codigo.set(401);
            assertThatThrownBy(() -> mail.enviarAhora("ana@correo.co", MailService.Correo.de("X", "Y", null, null, "Z")))
                    .hasMessageContaining("401");
        } finally {
            srv.stop(0);
        }
    }
}
