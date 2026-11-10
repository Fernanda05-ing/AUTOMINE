package com.automine.incapacidades;

import java.time.LocalDate;
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

/**
 * Incapacidad medica o licencia de un trabajador. La expide el medico de la EPS
 * o ARL; el trabajador (o el personal en su nombre) la registra con el
 * certificado como soporte y SG-SST / Talento Humano la revisa.
 */
@Entity
@Table(name = "incapacidades")
@Getter
@Setter
public class Incapacidad {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String ANULADA = "ANULADA";

    public static final String ENFERMEDAD_GENERAL = "ENFERMEDAD_GENERAL";
    public static final String ACCIDENTE_TRABAJO = "ACCIDENTE_TRABAJO";
    public static final String ENFERMEDAD_LABORAL = "ENFERMEDAD_LABORAL";
    public static final String LICENCIA_MATERNIDAD = "LICENCIA_MATERNIDAD";
    public static final String LICENCIA_PATERNIDAD = "LICENCIA_PATERNIDAD";
    public static final List<String> TIPOS = List.of(ENFERMEDAD_GENERAL, ACCIDENTE_TRABAJO, ENFERMEDAD_LABORAL,
            LICENCIA_MATERNIDAD, LICENCIA_PATERNIDAD);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    private String tipo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer dias;
    /** Continuacion de una incapacidad anterior: el conteo de dias para el pago sigue acumulando. */
    private boolean prorroga;
    private String entidad;
    private String numeroCertificado;
    private String observaciones;
    private String estado;
    private String motivoRechazo;
    private String soporteNombre;
    private String registradoPor;
    private String revisadoPor;
    private LocalDateTime fechaRevision;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Incapacidad, Long> {

        @EntityGraph(attributePaths = "empleado")
        @Query("""
                select i from Incapacidad i
                where (:empleadoId is null or i.empleado.id = :empleadoId)
                  and (:estado is null or i.estado = :estado)
                order by i.createdAt desc""")
        List<Incapacidad> buscar(Long empleadoId, String estado);

        @EntityGraph(attributePaths = "empleado")
        Optional<Incapacidad> findWithEmpleadoById(Long id);

        /** Incapacidades vigentes (pendientes o aprobadas) que se cruzan con el rango indicado. */
        @Query("""
                select i from Incapacidad i
                where i.empleado.id = :empleadoId and i.estado in ('PENDIENTE', 'APROBADA')
                  and i.fechaInicio <= :hasta and i.fechaFin >= :desde and i.id <> :excluir""")
        List<Incapacidad> traslapadas(Long empleadoId, LocalDate desde, LocalDate hasta, Long excluir);

        @Query("""
                select i from Incapacidad i
                where i.empleado.id = :empleadoId and i.estado = 'APROBADA'
                  and i.fechaInicio <= :hasta and i.fechaFin >= :desde
                order by i.fechaInicio""")
        List<Incapacidad> aprobadasEnRango(Long empleadoId, LocalDate desde, LocalDate hasta);

        /** Incapacidad aprobada inmediatamente anterior (para acumular el conteo de una prorroga). */
        Optional<Incapacidad> findFirstByEmpleadoIdAndEstadoAndFechaFinBeforeOrderByFechaFinDesc(Long empleadoId,
                String estado, LocalDate antes);

        long countByEstado(String estado);
    }

    /** Certificado de incapacidad (PDF o imagen). */
    @Entity(name = "SoporteIncapacidad")
    @Table(name = "soportes_incapacidad")
    @Getter
    @Setter
    public static class Soporte {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long incapacidadId;
        private String nombre;
        private String contentType;

        @Lob
        @Basic(fetch = FetchType.LAZY)
        @Column(columnDefinition = "LONGBLOB")
        private byte[] datos;

        private LocalDateTime createdAt;

        @PrePersist
        void prePersist() {
            createdAt = LocalDateTime.now();
        }

        public interface Repo extends JpaRepository<Soporte, Long> {

            Optional<Soporte> findByIncapacidadId(Long incapacidadId);
        }
    }
}
