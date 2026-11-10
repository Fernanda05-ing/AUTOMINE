package com.automine.salud;

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

/** Evaluacion medica ocupacional: concepto de aptitud, restricciones y proxima fecha. */
@Entity
@Table(name = "examenes_medicos")
@Getter
@Setter
public class ExamenMedico {

    public static final List<String> TIPOS = List.of("INGRESO", "PERIODICO", "RETIRO", "POST_INCAPACIDAD",
            "CAMBIO_OCUPACION", "ALTURAS");
    public static final List<String> CONCEPTOS = List.of("APTO", "APTO_CON_RESTRICCIONES", "APLAZADO", "NO_APTO");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    private String tipo;
    private LocalDate fecha;
    private String concepto;
    private String restricciones;
    private String recomendaciones;
    private String ips;
    private String medico;
    private LocalDate proximaFecha;
    private String soporteNombre;
    private String soporteTipo;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "LONGBLOB")
    private byte[] soporte;

    private String registradoPor;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<ExamenMedico, Long> {

        @EntityGraph(attributePaths = "empleado")
        @Query("""
                select x from ExamenMedico x
                where (:empleadoId is null or x.empleado.id = :empleadoId)
                order by x.fecha desc, x.id desc""")
        List<ExamenMedico> buscar(Long empleadoId);

        @EntityGraph(attributePaths = "empleado")
        Optional<ExamenMedico> findWithEmpleadoById(Long id);

        @EntityGraph(attributePaths = "empleado")
        @Query("select x from ExamenMedico x where x.empleado.estado = 'ACTIVO' and x.tipo <> 'RETIRO' order by x.fecha desc, x.id desc")
        List<ExamenMedico> deActivos();
    }
}
