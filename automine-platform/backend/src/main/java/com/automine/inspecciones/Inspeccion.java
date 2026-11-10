package com.automine.inspecciones;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.automine.empleados.Empleado;
import com.automine.inventario.Maquinaria;

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

/** Inspeccion preoperacional de un equipo antes de iniciar labores. */
@Entity
@Table(name = "inspecciones")
@Getter
@Setter
public class Inspeccion {

    public static final String APTO = "APTO";
    public static final String APTO_CON_OBSERVACIONES = "APTO_CON_OBSERVACIONES";
    public static final String NO_APTO = "NO_APTO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maquinaria_id")
    private Maquinaria maquinaria;

    private String equipo;
    private String tipo;
    private LocalDateTime fechaHora;
    private BigDecimal horometro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operador_id")
    private Empleado operador;

    /** Respuestas en JSON: [{item, critico, estado OK|FALLA|NA, observacion}]. */
    @Column(columnDefinition = "TEXT")
    private String items;
    private int fallas;
    private int fallasCriticas;
    private String resultado;
    private String observaciones;
    private String inspector;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Inspeccion, Long> {

        @EntityGraph(attributePaths = { "maquinaria", "operador" })
        @Query("""
                select i from Inspeccion i
                where i.fechaHora >= :desde and i.fechaHora < :hasta
                  and (:maquinariaId is null or i.maquinaria.id = :maquinariaId)
                order by i.fechaHora desc""")
        List<Inspeccion> buscar(LocalDateTime desde, LocalDateTime hasta, Long maquinariaId);

        @EntityGraph(attributePaths = { "maquinaria", "operador" })
        Optional<Inspeccion> findWithMaquinariaById(Long id);
    }

    @Entity(name = "FotoInspeccion")
    @Table(name = "fotos_inspeccion")
    @Getter
    @Setter
    public static class Foto {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long inspeccionId;
        private String item;
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

        public interface Repo extends JpaRepository<Foto, Long> {

            @Query("select f.id from FotoInspeccion f where f.inspeccionId = :inspeccionId order by f.id")
            List<Long> idsDe(Long inspeccionId);

            Optional<Foto> findByIdAndInspeccionId(Long id, Long inspeccionId);
        }
    }
}
