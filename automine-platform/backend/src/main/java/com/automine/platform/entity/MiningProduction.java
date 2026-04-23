package com.automine.platform.entity;

import com.automine.platform.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "mining_production")
public class MiningProduction extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    @Column(name = "shift_name", nullable = false, length = 20)
    private String shiftName;

    @Column(name = "tons_extracted", nullable = false, precision = 14, scale = 2)
    private BigDecimal tonsExtracted;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_employee_id")
    private Employee supervisor;

    @Column(name = "machinery_used", length = 200)
    private String machineryUsed;

    @Column(length = 500)
    private String observations;
}
