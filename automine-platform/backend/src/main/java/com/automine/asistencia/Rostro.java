package com.automine.asistencia;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Plantilla facial y foto de referencia de un trabajador, con su autorizacion (Ley 1581). */
@Entity
@Table(name = "rostros")
@Getter
@Setter
public class Rostro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    /** Vector numerico (JSON) calculado en el celular. */
    @Column(columnDefinition = "TEXT")
    private String plantilla;
    private String versionModelo;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "LONGBLOB")
    private byte[] foto;

    private LocalDateTime autorizacionFecha;
    private String autorizacionTexto;
    private String registradoPor;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Rostro, Long> {

        Optional<Rostro> findByEmpleadoId(Long empleadoId);

        boolean existsByEmpleadoId(Long empleadoId);

        @EntityGraph(attributePaths = "empleado")
        @Query("select r from Rostro r where r.empleado.estado = 'ACTIVO' order by r.empleado.apellidos")
        List<Rostro> activos();

        @Modifying
        @Query("delete from Rostro r where r.empleado.id = :empleadoId")
        int borrarDeEmpleado(Long empleadoId);
    }
}
