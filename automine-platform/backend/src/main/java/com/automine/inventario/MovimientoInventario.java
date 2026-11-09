package com.automine.inventario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Entradas, salidas y ajustes (RF027). La secuencia por producto es el kardex (RF029). */
@Entity
@Table(name = "movimientos_inventario")
@Getter
@Setter
public class MovimientoInventario {

    public static final String ENTRADA = "ENTRADA";
    public static final String SALIDA = "SALIDA";
    public static final String AJUSTE = "AJUSTE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    private String tipo;
    private BigDecimal cantidad;
    private BigDecimal saldo;
    private BigDecimal costoUnitario;
    private String motivo;
    private String responsable;
    private LocalDateTime fecha;

    public interface Repo extends JpaRepository<MovimientoInventario, Long> {

        @EntityGraph(attributePaths = "producto")
        @Query("select m from MovimientoInventario m where m.producto.id = :productoId "
                + "and m.fecha between :desde and :hasta order by m.fecha, m.id")
        List<MovimientoInventario> kardex(Long productoId, LocalDateTime desde, LocalDateTime hasta);

        @EntityGraph(attributePaths = "producto")
        @Query("select m from MovimientoInventario m order by m.fecha desc, m.id desc")
        List<MovimientoInventario> recientes(Pageable pageable);
    }
}
