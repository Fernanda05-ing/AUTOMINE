package com.automine.platform.controller;

import com.automine.platform.entity.InventoryProduct;
import com.automine.platform.service.EnterpriseModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final EnterpriseModuleService service;

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    public ResponseEntity<InventoryProduct> createProduct(@RequestBody InventoryProduct product) {
        return ResponseEntity.ok(service.saveInventoryProduct(product));
    }

    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','SUPERVISOR')")
    public ResponseEntity<List<InventoryProduct>> listProducts() {
        return ResponseEntity.ok(service.listInventoryProducts());
    }
}
