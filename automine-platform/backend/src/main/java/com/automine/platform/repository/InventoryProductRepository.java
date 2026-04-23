package com.automine.platform.repository;

import com.automine.platform.entity.InventoryProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryProductRepository extends JpaRepository<InventoryProduct, Long> {
    List<InventoryProduct> findByDeletedAtIsNull();
}
