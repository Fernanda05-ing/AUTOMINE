package com.automine.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "produccion")
public class MiningProduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "produccion_id")
    private Integer produccionId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 30)
    private String turno;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal toneladas;

    @Column(length = 120)
    private String supervisor;

    @Column(length = 120)
    private String maquinaria;

    @Column(length = 255)
    private String observaciones;
}
