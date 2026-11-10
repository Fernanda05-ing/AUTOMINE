package com.automine.common;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Donde se guardan los archivos (fotos, certificados, comprobantes):
 * - Sin configuracion: dentro de la base de datos, como siempre (instalacion local con MySQL).
 * - Con SUPABASE_URL y SUPABASE_SERVICE_KEY: en un bucket privado de Supabase Storage; en la base
 *   queda solo una referencia corta ("supabase:ruta"). Asi la base gratuita (500 MB) no se llena.
 * Las entidades no cambian: {@link ArchivoConverter} hace la conversion al guardar y el contenido
 * real se obtiene con {@link #contenido(byte[])} al descargar.
 */
@Component
public class ArchivoStore {

    private static final Logger log = LoggerFactory.getLogger(ArchivoStore.class);
    private static final byte[] PREFIJO = "supabase:".getBytes(StandardCharsets.US_ASCII);
    private static volatile ArchivoStore instancia;

    private final String url;
    private final String llave;
    private final String bucket;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ArchivoStore(@Value("${app.storage.supabase-url:}") String url,
            @Value("${app.storage.supabase-key:}") String llave,
            @Value("${app.storage.bucket:automine}") String bucket) {
        this.url = url == null ? "" : url.replaceAll("/+$", "");
        this.llave = llave == null ? "" : llave.trim();
        this.bucket = bucket;
        instancia = this;
    }

    public boolean remoto() {
        return !url.isBlank() && !llave.isBlank();
    }

    static ArchivoStore instancia() {
        return instancia;
    }

    /** Crea el bucket privado si no existe. */
    @EventListener(ApplicationReadyEvent.class)
    public void prepararBucket() {
        if (!remoto()) {
            return;
        }
        try {
            HttpResponse<String> r = http.send(base("/storage/v1/bucket/" + bucket).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 400) {
                HttpResponse<String> c = http.send(base("/storage/v1/bucket").header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"id\":\"" + bucket + "\",\"name\":\"" + bucket
                                + "\",\"public\":false,\"file_size_limit\":10485760}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
                log.info("Bucket de Supabase Storage '{}': {}", bucket, c.statusCode() < 300 ? "creado" : c.body());
            } else {
                log.info("Archivos en Supabase Storage (bucket privado '{}')", bucket);
            }
        } catch (Exception e) {
            log.warn("No se pudo verificar el bucket de Supabase: {}", e.getMessage());
        }
    }

    /** Si el valor es una referencia a Storage. */
    public static boolean esReferencia(byte[] datos) {
        return datos != null && datos.length < 400 && datos.length > PREFIJO.length
                && Arrays.equals(Arrays.copyOf(datos, PREFIJO.length), PREFIJO);
    }

    /** Sube el archivo a Storage y devuelve la referencia que se guarda en la base. */
    byte[] subir(byte[] datos) {
        String ruta = YearMonth.now() + "/" + UUID.randomUUID() + ".bin";
        try {
            HttpResponse<String> r = http.send(base("/storage/v1/object/" + bucket + "/" + ruta)
                    .header("Content-Type", "application/octet-stream").header("x-upsert", "true")
                    .timeout(Duration.ofSeconds(60)).POST(HttpRequest.BodyPublishers.ofByteArray(datos)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 300) {
                throw new IllegalStateException("Supabase Storage respondió " + r.statusCode() + ": " + r.body());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo guardar el archivo en Supabase Storage", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la subida del archivo", e);
        }
        return ("supabase:" + ruta).getBytes(StandardCharsets.US_ASCII);
    }

    /** Contenido real del archivo: descarga de Storage si el valor es una referencia. */
    public static byte[] contenido(byte[] valor) {
        if (!esReferencia(valor)) {
            return valor;
        }
        ArchivoStore s = instancia;
        if (s == null || !s.remoto()) {
            throw ApiException.notFound("Archivo (almacenamiento externo no configurado)");
        }
        String ruta = new String(valor, PREFIJO.length, valor.length - PREFIJO.length, StandardCharsets.US_ASCII);
        try {
            HttpResponse<byte[]> r = s.http.send(s.base("/storage/v1/object/" + s.bucket + "/" + ruta).GET()
                    .timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() >= 300) {
                throw ApiException.notFound("Archivo");
            }
            return r.body();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo de Supabase Storage", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la descarga del archivo", e);
        }
    }

    /** Borra el archivo de Storage (por ejemplo, el rostro de un trabajador retirado). */
    public static void borrar(byte[] valor) {
        ArchivoStore s = instancia;
        if (!esReferencia(valor) || s == null || !s.remoto()) {
            return;
        }
        String ruta = new String(valor, PREFIJO.length, valor.length - PREFIJO.length, StandardCharsets.US_ASCII);
        try {
            s.http.send(s.base("/storage/v1/object/" + s.bucket).header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString("{\"prefixes\":[\"" + ruta + "\"]}")).build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("No se pudo borrar {} de Supabase Storage: {}", ruta, e.getMessage());
        }
    }

    private HttpRequest.Builder base(String ruta) {
        return HttpRequest.newBuilder(URI.create(url + ruta)).header("Authorization", "Bearer " + llave).header("apikey", llave);
    }
}
