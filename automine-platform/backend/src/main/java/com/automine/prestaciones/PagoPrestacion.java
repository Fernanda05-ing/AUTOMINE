package com.automine.prestaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Prima, cesantias o intereses liquidados a un trabajador en un periodo. */
@Entity
@Table(name = "pagos_prestaciones")
@Getter
@Setter
public class PagoPrestacion {

    public static final String PRIMA = "PRIMA";
    public static final String CESANTIAS = "CESANTIAS";
    public static final String INTERESES = "INTERESES";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    private String tipo;
    private Integer anio;
    /** Prima: semestre 1 o 2. Cesantias e intereses: 0 (anual). */
    private Integer periodo;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private BigDecimal base;
    private Integer dias;
    private BigDecimal valor;
    private String liquidadoPor;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<PagoPrestacion, Long> {

        boolean existsByEmpleadoIdAndTipoAndAnioAndPeriodo(Long empleadoId, String tipo, Integer anio, Integer periodo);

        @EntityGraph(attributePaths = "empleado")
        List<PagoPrestacion> findByTipoAndAnioAndPeriodoOrderByEmpleadoApellidosAsc(String tipo, Integer anio,
                Integer periodo);

        @EntityGraph(attributePaths = "empleado")
        List<PagoPrestacion> findByEmpleadoIdOrderByAnioDescPeriodoDescTipoAsc(Long empleadoId);

        @Query("""
                select coalesce(sum(p.valor), 0) from PagoPrestacion p
                where p.empleado.id = :empleadoId and p.tipo = :tipo and p.anio = :anio and p.periodo = :periodo""")
        BigDecimal pagado(Long empleadoId, String tipo, Integer anio, Integer periodo);
    }
}
