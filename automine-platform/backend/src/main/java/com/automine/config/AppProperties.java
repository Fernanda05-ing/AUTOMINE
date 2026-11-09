package com.automine.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String env,
        Jwt jwt,
        String publicUrl,
        Google google,
        List<String> corsOrigins,
        String mailFrom,
        Company company,
        Bootstrap bootstrap) {

    /** production o demo: servidor publico, se exigen claves propias. */
    public boolean produccion() {
        return "production".equalsIgnoreCase(env) || "demo".equalsIgnoreCase(env);
    }

    /** Solo en demo (o desarrollo) se permiten datos de ejemplo con usuarios de prueba. */
    public boolean permiteDemo() {
        return !"production".equalsIgnoreCase(env);
    }

    public record Google(String clientId) {

        public boolean habilitado() {
            return clientId != null && !clientId.isBlank();
        }
    }

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Company(String name, String nit, String city, String signerName, String signerTitle) {
    }

    public record Bootstrap(String adminEmail, String adminPassword, boolean demoData) {
    }
}
