package com.automine.sgsst;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** RF040: matriz de identificacion de peligros y valoracion de riesgos. */
@Entity
@Table(name = "riesgos")
@Getter
@Setter
public class Riesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String proceso;
    private String actividad;
    private String peligro;
    private String clasificacion;
    private String efectos;
    private Integer probabilidad;
    private Integer severidad;
    private String controles;
    private String responsable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Nivel de riesgo = probabilidad x severidad (1 a 25). */
    public int getNivel() {
        return probabilidad * severidad;
    }

    public String getInterpretacion() {
        int n = getNivel();
        if (n >= 17) {
            return "CRITICO";
        }
        if (n >= 10) {
            return "ALTO";
        }
        if (n >= 5) {
            return "MEDIO";
        }
        return "BAJO";
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Riesgo, Long> {

        List<Riesgo> findAllByOrderByProcesoAscActividadAsc();
    }
}
