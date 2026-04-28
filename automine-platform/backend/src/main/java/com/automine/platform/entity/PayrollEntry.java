package com.automine.platform.entity;

import com.automine.platform.entity.enums.PayrollEntryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "nomina", uniqueConstraints = @UniqueConstraint(columnNames = {"empleado_id", "periodo"}))
public class PayrollEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nomina_id")
    private Integer nominaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, length = 20)
    private String periodo;

    @Column(name = "salario_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal salarioBase;

    @Column(name = "horas_extras", precision = 12, scale = 2)
    private BigDecimal horasExtras = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal bonificaciones = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal descuentos = BigDecimal.ZERO;

    @Column(name = "neto_pagar", nullable = false, precision = 12, scale = 2)
    private BigDecimal netoPagar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('GENERADA','PAGADA','ANULADA') DEFAULT 'GENERADA'")
    private PayrollEntryStatus estado = PayrollEntryStatus.GENERADA;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;

    @PrePersist
    protected void onCreate() {
        fechaGeneracion = LocalDateTime.now();
    }
}
