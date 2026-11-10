package com.automine.consignaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.automine.nomina.Nomina;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Consignacion bancaria del pago de una nomina (RF017 a RF021). */
@Entity
@Table(name = "consignaciones")
@Getter
@Setter
public class Consignacion {

    public static final String REGISTRADA = "REGISTRADA";
    public static final String APROBADA = "APROBADA";
    public static final String ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nomina_id")
    private Nomina nomina;

    private String banco;
    private String cuenta;
    private BigDecimal valor;
    private LocalDate fechaPago;
    private String referencia;
    private String estado;
    private String comprobanteNombre;
    private String observaciones;
    private String registradoPor;
    private String aprobadoPor;
    private LocalDateTime fechaAprobacion;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Consignacion, Long> {

        boolean existsByReferenciaIgnoreCase(String referencia);

        @Query("select coalesce(sum(c.valor), 0) from Consignacion c where c.nomina.id = :nominaId "
                + "and c.estado <> 'ANULADA'")
        BigDecimal totalConsignado(Long nominaId);

        @Query("select coalesce(sum(c.valor), 0) from Consignacion c where c.nomina.id = :nominaId "
                + "and c.estado = 'APROBADA'")
        BigDecimal totalAprobado(Long nominaId);

        @EntityGraph(attributePaths = { "nomina", "nomina.empleado", "nomina.periodo" })
        @Query("select c from Consignacion c where (:periodoId is null or c.nomina.periodo.id = :periodoId) "
                + "and (:estado is null or c.estado = :estado) order by c.fechaPago desc, c.id desc")
        List<Consignacion> buscar(Long periodoId, String estado);

        @EntityGraph(attributePaths = { "nomina", "nomina.empleado", "nomina.periodo" })
        @Query("select c from Consignacion c where c.nomina.empleado.id = :empleadoId order by c.fechaPago desc")
        List<Consignacion> delEmpleado(Long empleadoId);

        @EntityGraph(attributePaths = { "nomina", "nomina.empleado", "nomina.periodo" })
        Optional<Consignacion> findWithNominaById(Long id);
    }

    /** RF020: archivo del comprobante, en tabla aparte para no cargarlo en los listados. */
    @Entity(name = "ComprobanteConsignacion")
    @Table(name = "comprobantes_consignacion")
    @Getter
    @Setter
    public static class Comprobante {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long consignacionId;
        private String nombre;
        private String contentType;

        @jakarta.persistence.Convert(converter = com.automine.common.ArchivoConverter.class)
        @Basic(fetch = FetchType.LAZY)
        @Column(columnDefinition = "LONGBLOB")
        private byte[] datos;

        private LocalDateTime createdAt;

        @PrePersist
        void prePersist() {
            createdAt = LocalDateTime.now();
        }

        public interface Repo extends JpaRepository<Comprobante, Long> {

            Optional<Comprobante> findByConsignacionId(Long consignacionId);
        }
    }
}
