package com.automine.platform.entity;

import com.automine.platform.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "ppe_deliveries")
public class PpeDelivery extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "ppe_name", nullable = false, length = 120)
    private String ppeName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "condition_notes", length = 255)
    private String conditionNotes;

    @Column(name = "digital_signature_url")
    private String digitalSignatureUrl;
}
