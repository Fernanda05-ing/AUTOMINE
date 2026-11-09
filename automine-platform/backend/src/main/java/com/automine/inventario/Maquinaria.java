package com.automine.inventario;

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

/** RF030: inventario de maquinaria. */
@Entity
@Table(name = "maquinaria")
@Getter
@Setter
public class Maquinaria {

    public static final String OPERATIVA = "OPERATIVA";
    public static final String MANTENIMIENTO = "MANTENIMIENTO";
    public static final String FUERA_SERVICIO = "FUERA_SERVICIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    private String nombre;
    private String tipo;
    private String marca;
    private String modelo;
    private String serial;
    private String estado;
    private BigDecimal horometro;
    private LocalDate ultimoMantenimiento;
    private LocalDate proximoMantenimiento;
    private Long frenteId;
    private String observaciones;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Maquinaria, Long> {

        boolean existsByCodigoIgnoreCase(String codigo);

        List<Maquinaria> findAllByOrderByCodigoAsc();

        long countByEstado(String estado);

        @Query("select m from Maquinaria m where m.estado <> 'FUERA_SERVICIO' and m.proximoMantenimiento is not null "
                + "and m.proximoMantenimiento <= :limite order by m.proximoMantenimiento")
        List<Maquinaria> mantenimientoProximo(LocalDate limite);
    }
}
