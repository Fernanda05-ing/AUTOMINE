package com.automine.sgsst;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

/** RF038: entrega de elementos de proteccion personal. */
@Entity
@Table(name = "entregas_epp")
@Getter
@Setter
public class EntregaEpp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    private Long productoId;
    private String elemento;
    private BigDecimal cantidad;
    private LocalDate fecha;
    private String observaciones;
    private String entregadoPor;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<EntregaEpp, Long> {

        @EntityGraph(attributePaths = "empleado")
        @Query("select e from EntregaEpp e where (:empleadoId is null or e.empleado.id = :empleadoId) "
                + "and e.fecha between :desde and :hasta order by e.fecha desc, e.id desc")
        List<EntregaEpp> buscar(Long empleadoId, LocalDate desde, LocalDate hasta);
    }
}
