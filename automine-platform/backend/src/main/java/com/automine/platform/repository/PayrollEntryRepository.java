package com.automine.platform.repository;

import com.automine.platform.entity.PayrollEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollEntryRepository extends JpaRepository<PayrollEntry, Long> {
}
