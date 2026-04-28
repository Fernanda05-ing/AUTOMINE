package com.automine.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "certificaciones")
public class LaborCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "certificado_id")
    private Integer certificadoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Employee employee;

    @Column(name = "codigo_validacion", nullable = false, unique = true, length = 120)
    private String codigoValidacion;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;

    @Column(length = 255)
    private String observacion;

    @Column(nullable = false, length = 20, columnDefinition = "ENUM('GENERADO','DESCARGADO','ANULADO') DEFAULT 'GENERADO'")
    private String estado = "GENERADO";

    @PrePersist
    protected void onCreate() {
        fechaGeneracion = LocalDateTime.now();
    }
}
