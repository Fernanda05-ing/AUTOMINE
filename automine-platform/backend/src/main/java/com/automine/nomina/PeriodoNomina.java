package com.automine.nomina;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
@Table(name = "periodos_nomina")
@Getter
@Setter
public class PeriodoNomina {

    /** Se pueden agregar, editar y borrar liquidaciones. */
    public static final String ABIERTO = "ABIERTO";
    /** Aprobado por Contabilidad: queda bloqueado y listo para consignar. */
    public static final String APROBADO = "APROBADO";
    /** Todas las liquidaciones fueron consignadas y aprobadas. */
    public static final String PAGADO = "PAGADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer anio;
    private Integer mes;
    private String estado;
    private String aprobadoPor;
    private LocalDateTime fechaAprobacion;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<PeriodoNomina, Long> {

        Optional<PeriodoNomina> findByAnioAndMes(int anio, int mes);

        List<PeriodoNomina> findAllByOrderByAnioDescMesDesc();
    }
}
