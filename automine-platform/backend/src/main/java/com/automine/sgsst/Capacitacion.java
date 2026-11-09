package com.automine.sgsst;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.automine.empleados.Empleado;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** RF039: programacion de capacitaciones y control de asistencia. */
@Entity
@Table(name = "capacitaciones")
@Getter
@Setter
public class Capacitacion {

    public static final String PROGRAMADA = "PROGRAMADA";
    public static final String REALIZADA = "REALIZADA";
    public static final String CANCELADA = "CANCELADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tema;
    private String descripcion;
    private LocalDateTime fecha;
    private BigDecimal duracionHoras;
    private String instructor;
    private String lugar;
    private String estado;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "capacitacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Asistente> asistentes = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    @Entity(name = "AsistenteCapacitacion")
    @Table(name = "asistentes_capacitacion")
    @Getter
    @Setter
    public static class Asistente {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "capacitacion_id")
        private Capacitacion capacitacion;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "empleado_id")
        private Empleado empleado;

        private Boolean asistio;
    }

    public interface Repo extends JpaRepository<Capacitacion, Long> {

        @Query("select c from Capacitacion c where c.fecha between :desde and :hasta order by c.fecha desc")
        List<Capacitacion> buscar(LocalDateTime desde, LocalDateTime hasta);

        @Query("select c from Capacitacion c where c.estado = 'PROGRAMADA' and c.fecha >= :desde order by c.fecha")
        List<Capacitacion> proximas(LocalDateTime desde);

        @Query("select distinct c from Capacitacion c join c.asistentes a where a.empleado.id = :empleadoId "
                + "order by c.fecha desc")
        List<Capacitacion> delEmpleado(Long empleadoId);
    }
}
