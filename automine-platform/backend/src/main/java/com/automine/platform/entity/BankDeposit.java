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
@Table(name = "bank_deposits")
public class BankDeposit extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_entry_id")
    private PayrollEntry payrollEntry;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 40)
    private String accountNumber;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Column(name = "account_holder", nullable = false, length = 160)
    private String accountHolder;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "receipt_url")
    private String receiptUrl;

    @Column(name = "deposit_date", nullable = false)
    private LocalDate depositDate;

    @Column(nullable = false, length = 20)
    private String status = "REGISTERED";
}
