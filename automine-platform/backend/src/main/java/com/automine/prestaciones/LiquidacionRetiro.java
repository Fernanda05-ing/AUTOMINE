package com.automine.prestaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

/** Liquidacion final del contrato de trabajo. */
@Entity
@Table(name = "liquidaciones_retiro")
@Getter
@Setter
public class LiquidacionRetiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    private LocalDate fechaRetiro;
    private String motivo;
    private String observaciones;
    private BigDecimal salarioBase;
    private BigDecimal basePrestaciones;
    private Integer diasSalario;
    private BigDecimal salarioPendiente;
    private Integer diasPrima;
    private BigDecimal prima;
    private Integer diasCesantias;
    private BigDecimal cesantias;
    private BigDecimal interesesCesantias;
    private BigDecimal diasVacaciones;
    private BigDecimal vacaciones;
    private BigDecimal indemnizacion;
    private BigDecimal total;
    private String liquidadoPor;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<LiquidacionRetiro, Long> {

        @EntityGraph(attributePaths = "empleado")
        List<LiquidacionRetiro> findAllByOrderByFechaRetiroDesc();

        @EntityGraph(attributePaths = "empleado")
        Optional<LiquidacionRetiro> findWithEmpleadoById(Long id);

        @EntityGraph(attributePaths = "empleado")
        List<LiquidacionRetiro> findByEmpleadoIdOrderByFechaRetiroDesc(Long empleadoId);
    }
}
