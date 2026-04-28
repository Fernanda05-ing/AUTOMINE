package com.automine.platform.repository;

import com.automine.platform.entity.AccountingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountingTransactionRepository extends JpaRepository<AccountingTransaction, Long> {
}
