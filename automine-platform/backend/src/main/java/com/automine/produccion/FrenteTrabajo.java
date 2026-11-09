package com.automine.produccion;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "frentes_trabajo")
@Getter
@Setter
public class FrenteTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private String ubicacion;
    private String mineral;
    private BigDecimal metaDiariaToneladas;
    private Boolean activo;

    public interface Repo extends JpaRepository<FrenteTrabajo, Long> {

        boolean existsByNombreIgnoreCase(String nombre);

        List<FrenteTrabajo> findAllByOrderByNombreAsc();
    }
}
