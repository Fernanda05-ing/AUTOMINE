package com.automine.platform.entity;

import com.automine.platform.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "inventory_products")
public class InventoryProduct extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String sku;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 120)
    private String category;

    @Column(name = "unit_measure", nullable = false, length = 20)
    private String unitMeasure;

    @Column(name = "min_stock", nullable = false, precision = 14, scale = 2)
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(name = "current_stock", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}
