package com.automine.sgsst;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.automine.empleados.Empleado;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Accidentes (RF036) e incidentes (RF037) de trabajo. */
@Entity
@Table(name = "eventos_sst")
@Getter
@Setter
public class EventoSst {

    public static final String ACCIDENTE = "ACCIDENTE";
    public static final String INCIDENTE = "INCIDENTE";
    public static final String ABIERTO = "ABIERTO";
    public static final String CERRADO = "CERRADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    private LocalDateTime fecha;
    private String lugar;
    private String descripcion;
    private String gravedad;
    private Integer diasIncapacidad;
    private String causas;
    private String acciones;
    private String estado;
    private String reportadoPor;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<EventoSst, Long> {

        @EntityGraph(attributePaths = "empleado")
        @Query("select e from EventoSst e where (:tipo is null or e.tipo = :tipo) "
                + "and e.fecha between :desde and :hasta order by e.fecha desc")
        List<EventoSst> buscar(String tipo, LocalDateTime desde, LocalDateTime hasta);

        @EntityGraph(attributePaths = "empleado")
        List<EventoSst> findByEmpleadoIdOrderByFechaDesc(Long empleadoId);

        long countByTipoAndFechaBetween(String tipo, LocalDateTime desde, LocalDateTime hasta);

        long countByEstado(String estado);

        @Query("select coalesce(sum(e.diasIncapacidad), 0) from EventoSst e where e.fecha between :desde and :hasta")
        long diasIncapacidad(LocalDateTime desde, LocalDateTime hasta);

        @EntityGraph(attributePaths = "empleado")
        @Query("select e from EventoSst e where e.tipo = 'ACCIDENTE' order by e.fecha desc limit 1")
        List<EventoSst> ultimoAccidente();
    }
}
