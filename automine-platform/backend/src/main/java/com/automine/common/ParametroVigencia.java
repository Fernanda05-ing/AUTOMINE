package com.automine.common;

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

/** Valor de un parametro a partir de una fecha (p. ej. salario minimo de cada año). */
@Entity
@Table(name = "parametros_vigencia")
@Getter
@Setter
public class ParametroVigencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String clave;
    private LocalDate desde;
    private String valor;
    private String norma;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<ParametroVigencia, Long> {

        Optional<ParametroVigencia> findFirstByClaveAndDesdeLessThanEqualOrderByDesdeDesc(String clave, LocalDate fecha);

        List<ParametroVigencia> findAllByOrderByClaveAscDesdeAsc();

        boolean existsByClaveAndDesde(String clave, LocalDate desde);
    }
}
