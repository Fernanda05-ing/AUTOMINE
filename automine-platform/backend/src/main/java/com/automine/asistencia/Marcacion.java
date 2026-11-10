package com.automine.asistencia;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.automine.empleados.Empleado;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Entrada o salida de un trabajador registrada por SG-SST. */
@Entity
@Table(name = "marcaciones")
@Getter
@Setter
public class Marcacion {

    public static final String ENTRADA = "ENTRADA";
    public static final String SALIDA = "SALIDA";
    /** Reconocida por el rostro con similitud suficiente. */
    public static final String FACIAL = "FACIAL";
    /** Rostro con baja similitud: la SISO confirmo visualmente; queda para revision. */
    public static final String VERIFICACION_MANUAL = "VERIFICACION_MANUAL";
    /** Sin foto (celular dañado, trabajador sin rostro registrado...): requiere observacion. */
    public static final String MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    private String tipo;
    private LocalDateTime fechaHora;
    private String metodo;
    private BigDecimal similitud;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "LONGBLOB")
    private byte[] foto;

    private String observacion;
    private boolean anulada;
    private String registradoPor;
    /** Identificador generado en el celular: evita duplicados al sincronizar sin conexion. */
    private String dispositivoId;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Marcacion, Long> {

        @EntityGraph(attributePaths = "empleado")
        @Query("""
                select m from Marcacion m
                where m.fechaHora >= :desde and m.fechaHora < :hasta
                  and (:empleadoId is null or m.empleado.id = :empleadoId)
                order by m.fechaHora""")
        List<Marcacion> entre(LocalDateTime desde, LocalDateTime hasta, Long empleadoId);

        Optional<Marcacion> findByDispositivoId(String dispositivoId);

        @EntityGraph(attributePaths = "empleado")
        Optional<Marcacion> findWithEmpleadoById(Long id);

        /** Ultima marcacion valida antes de una hora (para validar la secuencia entrada/salida). */
        Optional<Marcacion> findFirstByEmpleadoIdAndAnuladaFalseAndFechaHoraBeforeOrderByFechaHoraDesc(
                Long empleadoId, LocalDateTime antes);
    }
}
