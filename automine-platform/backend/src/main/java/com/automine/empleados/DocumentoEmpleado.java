package com.automine.empleados;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** RF010: documentos adjuntos del empleado (se guardan en la base de datos). */
@Entity
@Table(name = "documentos_empleado")
@Getter
@Setter
public class DocumentoEmpleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long empleadoId;
    private String nombre;
    private String tipo;
    private String contentType;
    private Long tamano;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "LONGBLOB")
    private byte[] datos;

    private String subidoPor;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public record Resumen(Long id, Long empleadoId, String nombre, String tipo, String contentType, Long tamano,
            String subidoPor, LocalDateTime createdAt) {
    }

    public interface Repo extends JpaRepository<DocumentoEmpleado, Long> {

        @Query("select new com.automine.empleados.DocumentoEmpleado$Resumen(d.id, d.empleadoId, d.nombre, d.tipo, "
                + "d.contentType, d.tamano, d.subidoPor, d.createdAt) from DocumentoEmpleado d "
                + "where d.empleadoId = :empleadoId order by d.createdAt desc")
        List<Resumen> listar(Long empleadoId);
    }
}
