package com.automine.nomina;

import java.math.BigDecimal;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Liquidacion de nomina de un empleado en un periodo. */
@Entity
@Table(name = "nominas")
@Getter
@Setter
public class Nomina {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String PAGADA = "PAGADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "periodo_id")
    private PeriodoNomina periodo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    private BigDecimal salarioBase;
    private Integer diasTrabajados;
    private BigDecimal salarioDevengado;
    private BigDecimal horasExtraDiurnas;
    private BigDecimal horasExtraNocturnas;
    private BigDecimal horasExtraDominicales;
    private BigDecimal valorHorasExtra;
    private BigDecimal bonificaciones;
    private BigDecimal auxilioTransporte;
    private BigDecimal totalDevengado;
    private BigDecimal salud;
    private BigDecimal pension;
    private BigDecimal fondoSolidaridad;
    private BigDecimal otrosDescuentos;
    private String detalleDescuentos;
    private BigDecimal totalDescuentos;
    private BigDecimal netoPagar;
    private String estado;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Nomina, Long> {

        boolean existsByPeriodoIdAndEmpleadoId(Long periodoId, Long empleadoId);

        @EntityGraph(attributePaths = { "empleado", "periodo" })
        List<Nomina> findByPeriodoIdOrderByEmpleadoApellidosAsc(Long periodoId);

        @EntityGraph(attributePaths = { "empleado", "periodo" })
        @Query("select n from Nomina n where n.empleado.id = :empleadoId "
                + "order by n.periodo.anio desc, n.periodo.mes desc")
        List<Nomina> historialEmpleado(Long empleadoId);

        @EntityGraph(attributePaths = { "empleado", "periodo" })
        @Query("select n from Nomina n where n.periodo.anio = :anio order by n.periodo.mes, n.empleado.apellidos")
        List<Nomina> delAnio(int anio);

        long countByPeriodoId(Long periodoId);

        /** Nomina aprobada pendiente de pago (pasivo). */
        @Query("select coalesce(sum(n.netoPagar), 0) from Nomina n where n.estado = 'APROBADA'")
        BigDecimal porPagar();

        @Query("select coalesce(sum(n.totalDevengado), 0) from Nomina n "
                + "where n.periodo.anio = :anio and n.periodo.mes = :mes")
        BigDecimal costoPeriodo(int anio, int mes);

        long countByPeriodoIdAndEstadoNot(Long periodoId, String estado);
    }
}
