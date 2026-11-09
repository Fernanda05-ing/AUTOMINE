package com.automine.security;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** RF005: bitacora de accesos (ingresos, fallos, cierres y recuperaciones). */
@Entity
@Table(name = "bitacora_accesos")
@Getter
@Setter
public class BitacoraAcceso {

    public static final String LOGIN_OK = "LOGIN_OK";
    public static final String LOGIN_FALLIDO = "LOGIN_FALLIDO";
    public static final String LOGIN_BLOQUEADO = "LOGIN_BLOQUEADO";
    public static final String LOGOUT = "LOGOUT";
    public static final String RECUPERACION = "RECUPERACION_SOLICITADA";
    public static final String CAMBIO_PASSWORD = "CAMBIO_PASSWORD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioId;
    private String email;
    private String evento;
    private String ip;
    private String agente;
    private LocalDateTime fecha;

    public interface Repo extends JpaRepository<BitacoraAcceso, Long> {

        Page<BitacoraAcceso> findAllByOrderByFechaDesc(Pageable pageable);

        long countByEmailIgnoreCaseAndEventoAndFechaAfter(String email, String evento, LocalDateTime desde);
    }
}
