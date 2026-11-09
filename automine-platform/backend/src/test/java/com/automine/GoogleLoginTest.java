package com.automine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.automine.common.ApiException;
import com.automine.config.AppProperties;
import com.automine.security.GoogleTokenVerifier;

/** Verificacion de tokens de "Iniciar sesion con Google". */
class GoogleLoginTest {

    private static AppProperties props(String clientId) {
        return new AppProperties("development", new AppProperties.Jwt("x".repeat(40), 60), "http://localhost",
                new AppProperties.Google(clientId), List.of(), "", null, null);
    }

    @Test
    void deshabilitadoSinIdDeCliente() {
        GoogleTokenVerifier v = new GoogleTokenVerifier(props(""));
        assertThatThrownBy(() -> v.verificar("cualquier-cosa"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rechazaTokenFalso() {
        GoogleTokenVerifier v = new GoogleTokenVerifier(props("123-abc.apps.googleusercontent.com"));
        assertThatThrownBy(() -> v.verificar("esto.no.es-un-token"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        // Token con estructura JWT pero sin firma valida
        String falso = "eyJhbGciOiJub25lIn0.eyJlbWFpbCI6ImF0YWNhbnRlQGV4YW1wbGUuY29tIn0.";
        assertThatThrownBy(() -> v.verificar(falso))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
