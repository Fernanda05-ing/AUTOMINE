package com.automine.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** RF043: movimientos de caja menor con saldo corrido. */
@Entity
@Table(name = "caja_menor")
@Getter
@Setter
public class CajaMenor {

    /** Entra dinero a la caja (apertura o reposicion). */
    public static final String REPOSICION = "REPOSICION";
    /** Sale dinero de la caja (gasto menor). */
    public static final String GASTO = "GASTO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate fecha;
    private String tipo;
    private String concepto;
    private BigDecimal valor;
    private BigDecimal saldo;
    private String soporte;
    private String responsable;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<CajaMenor, Long> {

        Optional<CajaMenor> findFirstByOrderByIdDesc();

        List<CajaMenor> findByFechaBetweenOrderByIdDesc(LocalDate desde, LocalDate hasta);
    }
}
