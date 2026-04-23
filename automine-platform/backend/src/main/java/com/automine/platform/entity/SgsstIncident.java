package com.automine.platform.entity;

import com.automine.platform.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "sgsst_incidents")
public class SgsstIncident extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "incident_type", nullable = false, length = 30)
    private String incidentType;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "severity_level", nullable = false, length = 20)
    private String severityLevel;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "disability_days", nullable = false)
    private Integer disabilityDays = 0;

    @Column(name = "risk_type", length = 100)
    private String riskType;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";
}
