package com.automine.produccion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.automine.empleados.Empleado;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Registro de produccion de un turno en un frente de trabajo (RF031, RF032). */
@Entity
@Table(name = "producciones")
@Getter
@Setter
public class Produccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate fecha;
    private String turno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "frente_id")
    private FrenteTrabajo frente;

    private BigDecimal toneladas;
    private BigDecimal horasOperacion;
    private String observaciones;
    private String registradoPor;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "produccion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Asignacion> asignaciones = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    /** RF033: personal asignado al turno. */
    @Entity(name = "AsignacionTurno")
    @Table(name = "asignaciones_turno")
    @Getter
    @Setter
    public static class Asignacion {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "produccion_id")
        private Produccion produccion;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "empleado_id")
        private Empleado empleado;

        private String labor;

        public interface Repo extends JpaRepository<Asignacion, Long> {

            @EntityGraph(attributePaths = { "produccion", "produccion.frente", "empleado" })
            @Query("select a from AsignacionTurno a where a.empleado.id = :empleadoId "
                    + "and a.produccion.fecha between :desde and :hasta order by a.produccion.fecha desc")
            List<Asignacion> delEmpleado(Long empleadoId, LocalDate desde, LocalDate hasta);
        }
    }

    public interface Repo extends JpaRepository<Produccion, Long> {

        boolean existsByFechaAndTurnoAndFrenteId(LocalDate fecha, String turno, Long frenteId);

        @EntityGraph(attributePaths = { "frente" })
        @Query("select p from Produccion p where p.fecha between :desde and :hasta "
                + "and (:frenteId is null or p.frente.id = :frenteId) order by p.fecha desc, p.turno")
        List<Produccion> buscar(LocalDate desde, LocalDate hasta, Long frenteId);

        @Query("select coalesce(sum(p.toneladas), 0) from Produccion p where p.fecha between :desde and :hasta")
        BigDecimal toneladas(LocalDate desde, LocalDate hasta);
    }
}
