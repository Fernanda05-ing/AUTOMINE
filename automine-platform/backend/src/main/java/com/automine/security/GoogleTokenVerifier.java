package com.automine.security;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.automine.common.ApiException;
import com.automine.config.AppProperties;

/**
 * Verifica el "ID token" que entrega el boton de Google: firma con las llaves publicas de
 * Google, emisor, audiencia (nuestro ID de cliente), vigencia y correo verificado.
 */
@Component
public class GoogleTokenVerifier {

    private static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> EMISORES = Set.of("accounts.google.com", "https://accounts.google.com");

    private final AppProperties props;
    private volatile NimbusJwtDecoder decoder;

    public GoogleTokenVerifier(AppProperties props) {
        this.props = props;
    }

    public record IdentidadGoogle(String email, String nombre) {
    }

    public IdentidadGoogle verificar(String credential) {
        if (!props.google().habilitado()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "El inicio de sesión con Google no está habilitado");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(credential);
        } catch (JwtException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "No se pudo verificar la cuenta de Google. Intente de nuevo");
        }
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "El correo de la cuenta de Google no está verificado");
        }
        return new IdentidadGoogle(jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
    }

    private NimbusJwtDecoder decoder() {
        if (decoder == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri(JWKS).build();
                    String clientId = props.google().clientId();
                    OAuth2TokenValidator<Jwt> emisor = jwt -> EMISORES.contains(String.valueOf(jwt.getClaims().get("iss")))
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Emisor no válido", null));
                    OAuth2TokenValidator<Jwt> audiencia = jwt -> jwt.getAudience() != null
                            && jwt.getAudience().contains(clientId)
                                    ? OAuth2TokenValidatorResult.success()
                                    : OAuth2TokenValidatorResult.failure(
                                            new OAuth2Error("invalid_token", "Audiencia no válida", null));
                    d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            List.of(new JwtTimestampValidator(), emisor, audiencia)));
                    decoder = d;
                }
            }
        }
        return decoder;
    }
}
