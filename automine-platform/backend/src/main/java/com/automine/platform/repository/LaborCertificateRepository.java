package com.automine.platform.repository;

import com.automine.platform.entity.LaborCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LaborCertificateRepository extends JpaRepository<LaborCertificate, Long> {
    Optional<LaborCertificate> findByCodigoValidacion(String codigoValidacion);
}
