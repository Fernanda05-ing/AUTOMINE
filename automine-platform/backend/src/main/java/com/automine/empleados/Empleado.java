package com.automine.empleados;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "empleados")
@Getter
@Setter
public class Empleado {

    public static final String ACTIVO = "ACTIVO";
    public static final String INACTIVO = "INACTIVO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String cedula;
    private String nombres;
    private String apellidos;
    private String email;
    private String telefono;
    private String direccion;
    private LocalDate fechaNacimiento;
    private String cargo;
    private String area;
    private BigDecimal salario;
    private LocalDate fechaIngreso;
    private LocalDate fechaRetiro;
    private String tipoContrato;
    private String banco;
    private String tipoCuenta;
    private String numeroCuenta;
    private String eps;
    private String fondoPension;
    private String arl;
    private String estado;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }

    public boolean isActivo() {
        return ACTIVO.equals(estado);
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public interface Repo extends JpaRepository<Empleado, Long> {

        boolean existsByCedula(String cedula);

        Optional<Empleado> findByCedula(String cedula);

        List<Empleado> findByEstadoOrderByApellidosAsc(String estado);

        List<Empleado> findAllByOrderByApellidosAsc();

        long countByEstado(String estado);

        @Query("select e from Empleado e where (:estado is null or e.estado = :estado) and ("
                + ":q is null or lower(e.nombres) like lower(concat('%', :q, '%')) "
                + "or lower(e.apellidos) like lower(concat('%', :q, '%')) "
                + "or e.cedula like concat('%', :q, '%') "
                + "or lower(e.cargo) like lower(concat('%', :q, '%'))) order by e.apellidos, e.nombres")
        List<Empleado> buscar(String q, String estado);
    }
}
