package com.automine.platform.entity;

import com.automine.platform.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "labor_certificates")
public class LaborCertificate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "certificate_code", nullable = false, unique = true, length = 70)
    private String certificateCode;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "validation_code", nullable = false, unique = true, length = 100)
    private String validationCode;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "public_query_enabled", nullable = false)
    private boolean publicQueryEnabled = true;

    @Column(nullable = false, length = 20)
    private String status = "ISSUED";
}
