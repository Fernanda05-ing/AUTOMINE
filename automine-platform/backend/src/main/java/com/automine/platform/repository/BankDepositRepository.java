package com.automine.platform.repository;

import com.automine.platform.entity.BankDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankDepositRepository extends JpaRepository<BankDeposit, Long> {
    List<BankDeposit> findByDeletedAtIsNull();
}
