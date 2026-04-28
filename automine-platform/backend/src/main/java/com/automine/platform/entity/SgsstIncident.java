package com.automine.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "accidentes")
public class SgsstIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "accidente_id")
    private Integer accidenteId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(length = 80)
    private String tipo;

    @Column(length = 255)
    private String descripcion;

    @Column(length = 20, columnDefinition = "ENUM('LEVE','MODERADO','GRAVE')")
    private String gravedad;

    @Column(name = "incapacidad_dias")
    private Integer incapacidadDias = 0;

    @Column(nullable = false, length = 20, columnDefinition = "ENUM('ABIERTO','CERRADO') DEFAULT 'ABIERTO'")
    private String estado = "ABIERTO";

    @PrePersist
    protected void onCreate() {
        fecha = LocalDateTime.now();
    }
}
