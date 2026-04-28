package com.automine.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "consignaciones")
public class BankDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consignacion_id")
    private Integer consignacionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nomina_id", nullable = false)
    private PayrollEntry payrollEntry;

    @Column(nullable = false, length = 80)
    private String banco;

    @Column(name = "numero_cuenta", length = 50)
    private String numeroCuenta;

    @Column(name = "tipo_cuenta", length = 20)
    private String tipoCuenta;

    @Column(length = 120)
    private String titular;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(length = 100)
    private String referencia;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    @Column(length = 255)
    private String comprobante;

    @PrePersist
    protected void onCreate() {
        fechaPago = LocalDateTime.now();
    }
}
