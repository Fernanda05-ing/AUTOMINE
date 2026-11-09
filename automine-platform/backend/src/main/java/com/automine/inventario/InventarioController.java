package com.automine.inventario;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.automine.inventario.InventarioDtos.MaquinariaRequest;
import com.automine.inventario.InventarioDtos.MantenimientoRequest;
import com.automine.inventario.InventarioDtos.MovimientoRequest;
import com.automine.inventario.InventarioDtos.MovimientoResponse;
import com.automine.inventario.InventarioDtos.ProductoRequest;
import com.automine.inventario.InventarioDtos.ProductoResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "6. Inventario y maquinaria")
public class InventarioController {

    private static final String LECTURA = "hasAnyRole('ADMIN','ALMACENISTA','GERENTE','SGSST','SUPERVISOR','CONTADOR')";
    private static final String ESCRITURA = "hasAnyRole('ADMIN','ALMACENISTA')";

    private final InventarioService service;

    @Operation(summary = "AddInventory - registrar producto o herramienta")
    @PostMapping("/Inventory")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ESCRITURA)
    public ProductoResponse create(@Valid @RequestBody ProductoRequest req) {
        return service.crear(req);
    }

    @Operation(summary = "ListInventory - consultar stock")
    @GetMapping("/Inventory")
    @PreAuthorize(LECTURA)
    public List<ProductoResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String tipo, @RequestParam(defaultValue = "false") boolean activos) {
        return service.listar(q, tipo, activos);
    }

    @GetMapping("/Inventory/{id}")
    @PreAuthorize(LECTURA)
    public ProductoResponse get(@PathVariable Long id) {
        return ProductoResponse.of(service.buscar(id));
    }

    @PutMapping("/Inventory/{id}")
    @PreAuthorize(ESCRITURA)
    public ProductoResponse update(@PathVariable Long id, @Valid @RequestBody ProductoRequest req) {
        return service.editar(id, req);
    }

    @PutMapping("/Inventory/{id}/Active/{activo}")
    @PreAuthorize(ESCRITURA)
    public ProductoResponse active(@PathVariable Long id, @PathVariable boolean activo) {
        return service.cambiarActivo(id, activo);
    }

    @Operation(summary = "Registrar entrada, salida o ajuste")
    @PostMapping("/Inventory/Movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ESCRITURA)
    public MovimientoResponse movement(@Valid @RequestBody MovimientoRequest req) {
        return service.movimiento(req);
    }

    @GetMapping("/Inventory/Movements")
    @PreAuthorize(LECTURA)
    public List<MovimientoResponse> recent(@RequestParam(defaultValue = "50") int limite) {
        return service.recientes(limite);
    }

    @Operation(summary = "Kardex del producto")
    @GetMapping("/Inventory/{id}/Kardex")
    @PreAuthorize(LECTURA)
    public List<MovimientoResponse> kardex(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.kardex(id, desde, hasta);
    }

    @Operation(summary = "Alertas de stock mínimo")
    @GetMapping("/Inventory/Alerts")
    @PreAuthorize(LECTURA)
    public List<ProductoResponse> alerts() {
        return service.alertasStock();
    }

    @GetMapping("/Machinery")
    @PreAuthorize(LECTURA)
    public List<Maquinaria> machines() {
        return service.maquinas();
    }

    @GetMapping("/Machinery/{id}")
    @PreAuthorize(LECTURA)
    public Maquinaria machine(@PathVariable Long id) {
        return service.maquina(id);
    }

    @PostMapping("/Machinery")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ESCRITURA)
    public Maquinaria createMachine(@Valid @RequestBody MaquinariaRequest req) {
        return service.crearMaquina(req);
    }

    @PutMapping("/Machinery/{id}")
    @PreAuthorize(ESCRITURA)
    public Maquinaria updateMachine(@PathVariable Long id, @Valid @RequestBody MaquinariaRequest req) {
        return service.editarMaquina(id, req);
    }

    @PostMapping("/Machinery/{id}/Maintenance")
    @PreAuthorize(ESCRITURA)
    public Maquinaria maintenance(@PathVariable Long id, @Valid @RequestBody MantenimientoRequest req) {
        return service.mantenimiento(id, req);
    }

    @GetMapping("/Machinery/Alerts")
    @PreAuthorize(LECTURA)
    public List<Maquinaria> maintenanceAlerts() {
        return service.alertasMantenimiento();
    }
}
