package com.automine.platform.service.impl;

import com.automine.platform.entity.*;
import com.automine.platform.exception.ApiException;
import com.automine.platform.repository.*;
import com.automine.platform.service.EnterpriseModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EnterpriseModuleServiceImpl implements EnterpriseModuleService {

    private final BankDepositRepository bankDepositRepository;
    private final LaborCertificateRepository laborCertificateRepository;
    private final InventoryProductRepository inventoryProductRepository;
    private final MiningProductionRepository miningProductionRepository;
    private final SgsstIncidentRepository sgsstIncidentRepository;
    private final AccountingTransactionRepository accountingTransactionRepository;

    @Override
    public BankDeposit saveDeposit(BankDeposit deposit) {
        return bankDepositRepository.save(deposit);
    }

    @Override
    public List<BankDeposit> listDeposits() {
        return bankDepositRepository.findByDeletedAtIsNull();
    }

    @Override
    public LaborCertificate saveCertificate(LaborCertificate certificate) {
        return laborCertificateRepository.save(certificate);
    }

    @Override
    public List<LaborCertificate> listCertificates() {
        return laborCertificateRepository.findByDeletedAtIsNull();
    }

    @Override
    public LaborCertificate findCertificateByValidationCode(String validationCode) {
        return laborCertificateRepository.findByValidationCodeAndDeletedAtIsNull(validationCode)
            .orElseThrow(() -> new ApiException("Certificado no encontrado"));
    }

    @Override
    public InventoryProduct saveInventoryProduct(InventoryProduct product) {
        return inventoryProductRepository.save(product);
    }

    @Override
    public List<InventoryProduct> listInventoryProducts() {
        return inventoryProductRepository.findByDeletedAtIsNull();
    }

    @Override
    public MiningProduction saveProduction(MiningProduction production) {
        return miningProductionRepository.save(production);
    }

    @Override
    public List<MiningProduction> listProduction() {
        return miningProductionRepository.findByDeletedAtIsNull();
    }

    @Override
    public SgsstIncident saveIncident(SgsstIncident incident) {
        return sgsstIncidentRepository.save(incident);
    }

    @Override
    public List<SgsstIncident> listIncidents() {
        return sgsstIncidentRepository.findByDeletedAtIsNull();
    }

    @Override
    public AccountingTransaction saveTransaction(AccountingTransaction transaction) {
        return accountingTransactionRepository.save(transaction);
    }

    @Override
    public List<AccountingTransaction> listTransactions() {
        return accountingTransactionRepository.findByDeletedAtIsNull();
    }

    @Override
    public Map<String, Object> dashboardSummary() {
        BigDecimal income = accountingTransactionRepository.findByDeletedAtIsNull().stream()
            .filter(t -> "INCOME".equals(t.getTransactionType()))
            .map(AccountingTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expense = accountingTransactionRepository.findByDeletedAtIsNull().stream()
            .filter(t -> "EXPENSE".equals(t.getTransactionType()))
            .map(AccountingTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal productionTons = miningProductionRepository.findByDeletedAtIsNull().stream()
            .map(MiningProduction::getTonsExtracted)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new HashMap<>();
        summary.put("income", income);
        summary.put("expense", expense);
        summary.put("balance", income.subtract(expense));
        summary.put("productionTons", productionTons);
        summary.put("incidentsOpen", sgsstIncidentRepository.findByDeletedAtIsNull().stream().filter(i -> "OPEN".equals(i.getStatus())).count());
        summary.put("lowStockProducts", inventoryProductRepository.findByDeletedAtIsNull().stream().filter(p -> p.getCurrentStock().compareTo(p.getMinStock()) <= 0).count());

        return summary;
    }
}
