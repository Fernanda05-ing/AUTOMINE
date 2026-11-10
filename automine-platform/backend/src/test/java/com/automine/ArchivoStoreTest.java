package com.automine;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.automine.common.ArchivoConverter;
import com.automine.common.ArchivoStore;
import com.sun.net.httpserver.HttpServer;

/** Archivos en Supabase Storage (servidor simulado con la misma API REST). */
class ArchivoStoreTest {

    @Test
    void subeDescargaYBorraEnStorage() throws Exception {
        Map<String, byte[]> objetos = new ConcurrentHashMap<>();
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/storage/v1/object/", ex -> {
            String ruta = ex.getRequestURI().getPath().substring("/storage/v1/object/automine/".length() - 1).replaceFirst("^/", "");
            assertThat(ex.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer llave-prueba");
            byte[] resp = new byte[0];
            int code = 200;
            switch (ex.getRequestMethod()) {
                case "POST" -> objetos.put(ruta, ex.getRequestBody().readAllBytes());
                case "GET" -> {
                    resp = objetos.get(ruta);
                    if (resp == null) {
                        code = 404;
                        resp = new byte[0];
                    }
                }
                case "DELETE" -> {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    objetos.keySet().removeIf(body::contains);
                }
                default -> code = 405;
            }
            ex.sendResponseHeaders(code, resp.length == 0 ? -1 : resp.length);
            if (resp.length > 0) {
                ex.getResponseBody().write(resp);
            }
            ex.close();
        });
        srv.start();
        try {
            new ArchivoStore("http://127.0.0.1:" + srv.getAddress().getPort(), "llave-prueba", "automine");
            byte[] foto = "contenido de la foto".getBytes(StandardCharsets.UTF_8);
            byte[] enBase = new ArchivoConverter().convertToDatabaseColumn(foto);
            // En la base solo queda la referencia
            assertThat(ArchivoStore.esReferencia(enBase)).isTrue();
            assertThat(new String(enBase, StandardCharsets.US_ASCII)).startsWith("supabase:");
            assertThat(objetos).hasSize(1);
            // Guardar de nuevo la referencia no vuelve a subir
            assertThat(new ArchivoConverter().convertToDatabaseColumn(enBase)).isEqualTo(enBase);
            assertThat(objetos).hasSize(1);
            // Se descarga el contenido real
            assertThat(ArchivoStore.contenido(enBase)).isEqualTo(foto);
            // Borrado (Ley 1581: rostro del trabajador retirado)
            ArchivoStore.borrar(enBase);
            assertThat(objetos).isEmpty();
        } finally {
            srv.stop(0);
            new ArchivoStore("", "", "automine"); // sin Storage para el resto de pruebas
        }
    }

    @Test
    void sinSupabaseLosArchivosQuedanEnLaBase() {
        new ArchivoStore("", "", "automine");
        byte[] foto = { 1, 2, 3 };
        assertThat(new ArchivoConverter().convertToDatabaseColumn(foto)).isEqualTo(foto);
        assertThat(ArchivoStore.contenido(foto)).isEqualTo(foto);
    }
}
