package com.automine.prestaciones;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.automine.empleados.Empleado;

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

/** Solicitud de vacaciones (dias habiles) de un trabajador. */
@Entity
@Table(name = "vacaciones")
@Getter
@Setter
public class Vacacion {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    private LocalDate fechaInicio;
    /** Ultimo dia de descanso (incluye domingos y festivos finales). */
    private LocalDate fechaFin;
    private LocalDate fechaRegreso;
    private Integer diasHabiles;
    private String observaciones;
    private String estado;
    private String motivoRechazo;
    private String solicitadoPor;
    private String revisadoPor;
    private LocalDateTime fechaRevision;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Vacacion, Long> {

        @EntityGraph(attributePaths = "empleado")
        @Query("""
                select v from Vacacion v
                where (:empleadoId is null or v.empleado.id = :empleadoId)
                  and (:estado is null or v.estado = :estado)
                order by v.fechaInicio desc""")
        List<Vacacion> buscar(Long empleadoId, String estado);

        @EntityGraph(attributePaths = "empleado")
        Optional<Vacacion> findWithEmpleadoById(Long id);

        @Query("""
                select coalesce(sum(v.diasHabiles), 0) from Vacacion v
                where v.empleado.id = :empleadoId and v.estado in ('PENDIENTE', 'APROBADA')""")
        long diasComprometidos(Long empleadoId);

        @Query("""
                select coalesce(sum(v.diasHabiles), 0) from Vacacion v
                where v.empleado.id = :empleadoId and v.estado = 'APROBADA'""")
        long diasDisfrutados(Long empleadoId);

        @Query("""
                select v from Vacacion v
                where v.empleado.id = :empleadoId and v.estado in ('PENDIENTE', 'APROBADA')
                  and v.fechaInicio <= :hasta and v.fechaFin >= :desde and v.id <> :excluir""")
        List<Vacacion> traslapadas(Long empleadoId, LocalDate desde, LocalDate hasta, Long excluir);

        @Query("""
                select v from Vacacion v
                where v.empleado.id = :empleadoId and v.estado = 'APROBADA'
                  and v.fechaInicio <= :hasta and v.fechaFin >= :desde
                order by v.fechaInicio""")
        List<Vacacion> aprobadasEnRango(Long empleadoId, LocalDate desde, LocalDate hasta);

        long countByEstado(String estado);
    }
}
