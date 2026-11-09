package com.automine.inventario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class InventarioDtos {

    private InventarioDtos() {
    }

    public static final String TIPOS = "HERRAMIENTA|EQUIPO|INSUMO|EPP|REPUESTO";

    /** Servicio AddInventory. */
    public record ProductoRequest(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Pattern(regexp = TIPOS) String tipo,
            @NotBlank @Size(max = 20) String unidad,
            @DecimalMin("0") BigDecimal cantidad,
            @NotNull @DecimalMin("0") BigDecimal stockMinimo,
            @NotNull @DecimalMin("0") BigDecimal costoUnitario,
            @Size(max = 80) String ubicacion) {
    }

    public record ProductoResponse(Long id, String codigo, String nombre, String tipo, String unidad,
            BigDecimal cantidad, BigDecimal stockMinimo, BigDecimal costoUnitario, BigDecimal valorTotal,
            String ubicacion, boolean activo, boolean bajoStock, LocalDateTime updatedAt) {

        public static ProductoResponse of(Producto p) {
            return new ProductoResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getTipo(), p.getUnidad(),
                    p.getCantidad(), p.getStockMinimo(), p.getCostoUnitario(),
                    p.getCantidad().multiply(p.getCostoUnitario()), p.getUbicacion(), p.getActivo(), p.isBajoStock(),
                    p.getUpdatedAt());
        }
    }

    public record MovimientoRequest(
            @NotNull Long productoId,
            @NotBlank @Pattern(regexp = "ENTRADA|SALIDA|AJUSTE") String tipo,
            @NotNull @DecimalMin(value = "0") BigDecimal cantidad,
            @DecimalMin("0") BigDecimal costoUnitario,
            @NotBlank @Size(max = 255) String motivo,
            @Size(max = 120) String responsable) {
    }

    public record MovimientoResponse(Long id, Long productoId, String productoCodigo, String producto, String tipo,
            BigDecimal cantidad, BigDecimal saldo, BigDecimal costoUnitario, String motivo, String responsable,
            LocalDateTime fecha) {

        public static MovimientoResponse of(MovimientoInventario m) {
            Producto p = m.getProducto();
            return new MovimientoResponse(m.getId(), p.getId(), p.getCodigo(), p.getNombre(), m.getTipo(),
                    m.getCantidad(), m.getSaldo(), m.getCostoUnitario(), m.getMotivo(), m.getResponsable(),
                    m.getFecha());
        }
    }

    public record MaquinariaRequest(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Size(max = 60) String tipo,
            @Size(max = 60) String marca,
            @Size(max = 60) String modelo,
            @Size(max = 60) String serial,
            @NotBlank @Pattern(regexp = "OPERATIVA|MANTENIMIENTO|FUERA_SERVICIO") String estado,
            @NotNull @DecimalMin("0") BigDecimal horometro,
            LocalDate proximoMantenimiento,
            Long frenteId,
            @Size(max = 255) String observaciones) {
    }

    public record MantenimientoRequest(
            @NotNull LocalDate fecha,
            @NotBlank @Size(max = 200) String descripcion,
            @DecimalMin("0") BigDecimal costo,
            @DecimalMin("0") BigDecimal horometro,
            LocalDate proximoMantenimiento) {
    }
}
