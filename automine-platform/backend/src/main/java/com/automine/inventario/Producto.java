package com.automine.inventario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Herramientas, equipos, insumos, EPP y repuestos del almacen (RF026). */
@Entity
@Table(name = "productos")
@Getter
@Setter
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    private String nombre;
    private String tipo;
    private String unidad;
    private BigDecimal cantidad;
    private BigDecimal stockMinimo;
    private BigDecimal costoUnitario;
    private String ubicacion;
    private Boolean activo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isBajoStock() {
        return cantidad.compareTo(stockMinimo) <= 0;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Producto, Long> {

        boolean existsByCodigoIgnoreCase(String codigo);

        @Query("select p from Producto p where (:tipo is null or p.tipo = :tipo) and (:activos = false or p.activo = true) "
                + "and (:q is null or lower(p.nombre) like lower(concat('%', :q, '%')) "
                + "or lower(p.codigo) like lower(concat('%', :q, '%'))) order by p.nombre")
        List<Producto> buscar(String q, String tipo, boolean activos);

        @Query("select p from Producto p where p.activo = true and p.cantidad <= p.stockMinimo order by p.nombre")
        List<Producto> bajoStock();

        @Query("select coalesce(sum(p.cantidad * p.costoUnitario), 0) from Producto p where p.activo = true")
        BigDecimal valorTotal();
    }
}
