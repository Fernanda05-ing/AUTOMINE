package com.automine.certificados;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "certificados")
@Getter
@Setter
public class Certificado {

    public static final String VIGENTE = "VIGENTE";
    public static final String ANULADO = "ANULADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    private String codigoValidacion;

    @jakarta.persistence.Column(name = "dirigido_a")
    private String dirigidoA;
    private Boolean incluyeSalario;
    private String estado;
    private String emitidoPor;
    private LocalDateTime fechaEmision;

    public interface Repo extends JpaRepository<Certificado, Long> {

        boolean existsByCodigoValidacion(String codigo);

        @EntityGraph(attributePaths = "empleado")
        Optional<Certificado> findByCodigoValidacion(String codigo);

        @EntityGraph(attributePaths = "empleado")
        Optional<Certificado> findWithEmpleadoById(Long id);

        @EntityGraph(attributePaths = "empleado")
        @Query("select c from Certificado c where (:empleadoId is null or c.empleado.id = :empleadoId) "
                + "order by c.fechaEmision desc")
        List<Certificado> buscar(Long empleadoId);

        long countByFechaEmisionAfter(LocalDateTime desde);
    }
}
