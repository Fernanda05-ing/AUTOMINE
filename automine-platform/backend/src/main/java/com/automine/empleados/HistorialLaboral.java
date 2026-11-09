package com.automine.empleados;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** RF009: historial laboral (ingresos, cambios de cargo o salario, retiros, novedades). */
@Entity
@Table(name = "historial_laboral")
@Getter
@Setter
public class HistorialLaboral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long empleadoId;
    private LocalDate fecha;
    private String tipo;
    private String descripcion;
    private String registradoPor;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<HistorialLaboral, Long> {

        List<HistorialLaboral> findByEmpleadoIdOrderByFechaDescIdDesc(Long empleadoId);
    }
}
