package com.automine.empleados;

import java.math.BigDecimal;
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

@Entity
@Table(name = "contratos")
@Getter
@Setter
public class Contrato {

    public static final String VIGENTE = "VIGENTE";
    public static final String TERMINADO = "TERMINADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long empleadoId;
    private String tipo;
    private String cargo;
    private BigDecimal salario;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Contrato, Long> {

        List<Contrato> findByEmpleadoIdOrderByFechaInicioDesc(Long empleadoId);

        List<Contrato> findByEmpleadoIdAndEstado(Long empleadoId, String estado);
    }
}
