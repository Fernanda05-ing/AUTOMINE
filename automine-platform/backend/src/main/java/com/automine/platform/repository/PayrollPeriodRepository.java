package com.automine.platform.repository;

import com.automine.platform.entity.PayrollPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {
    Optional<PayrollPeriod> findByPeriodCodeAndDeletedAtIsNull(String periodCode);
}
