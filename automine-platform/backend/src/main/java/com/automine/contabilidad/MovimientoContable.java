package com.automine.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Ingresos y gastos (RF041, RF042). */
@Entity
@Table(name = "movimientos_contables")
@Getter
@Setter
public class MovimientoContable {

    public static final String INGRESO = "INGRESO";
    public static final String GASTO = "GASTO";

    /** Registrado a mano por Contabilidad. */
    public static final String ORIGEN_MANUAL = "MANUAL";
    /** Generado al aprobar una consignacion de nomina. */
    public static final String ORIGEN_NOMINA = "NOMINA";
    /** Generado al reponer la caja menor. */
    public static final String ORIGEN_CAJA = "CAJA_MENOR";
    /** Generado por compras de inventario. */
    public static final String ORIGEN_INVENTARIO = "INVENTARIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tipo;
    private LocalDate fecha;
    private String concepto;
    private String categoria;
    private BigDecimal valor;
    private String referencia;
    private String origen;
    private String registradoPor;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public record TotalCategoria(String categoria, BigDecimal total) {
    }

    public record TotalMes(int anio, int mes, String tipo, BigDecimal total) {
    }

    public interface Repo extends JpaRepository<MovimientoContable, Long> {

        List<MovimientoContable> findByTipoAndFechaBetweenOrderByFechaDescIdDesc(String tipo, LocalDate desde,
                LocalDate hasta);

        @Query("select coalesce(sum(m.valor), 0) from MovimientoContable m where m.tipo = :tipo "
                + "and m.fecha between :desde and :hasta")
        BigDecimal total(String tipo, LocalDate desde, LocalDate hasta);

        @Query("select coalesce(sum(m.valor), 0) from MovimientoContable m where m.tipo = :tipo and m.fecha <= :hasta")
        BigDecimal acumulado(String tipo, LocalDate hasta);

        @Query("select new com.automine.contabilidad.MovimientoContable$TotalCategoria(m.categoria, sum(m.valor)) "
                + "from MovimientoContable m where m.tipo = :tipo and m.fecha between :desde and :hasta "
                + "group by m.categoria order by sum(m.valor) desc")
        List<TotalCategoria> porCategoria(String tipo, LocalDate desde, LocalDate hasta);

        @Query("select new com.automine.contabilidad.MovimientoContable$TotalMes(year(m.fecha), month(m.fecha), "
                + "m.tipo, sum(m.valor)) from MovimientoContable m where m.fecha between :desde and :hasta "
                + "group by year(m.fecha), month(m.fecha), m.tipo order by year(m.fecha), month(m.fecha)")
        List<TotalMes> porMes(LocalDate desde, LocalDate hasta);
    }
}
