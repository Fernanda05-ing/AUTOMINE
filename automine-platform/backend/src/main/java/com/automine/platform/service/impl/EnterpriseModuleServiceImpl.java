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
        return bankDepositRepository.findAll();
    }

    @Override
    public LaborCertificate saveCertificate(LaborCertificate certificate) {
        return laborCertificateRepository.save(certificate);
    }

    @Override
    public List<LaborCertificate> listCertificates() {
        return laborCertificateRepository.findAll();
    }

    @Override
    public LaborCertificate findCertificateByValidationCode(String validationCode) {
        return laborCertificateRepository.findByCodigoValidacion(validationCode)
            .orElseThrow(() -> new ApiException("Certificado no encontrado"));
    }

    @Override
    public InventoryProduct saveInventoryProduct(InventoryProduct product) {
        return inventoryProductRepository.save(product);
    }

    @Override
    public List<InventoryProduct> listInventoryProducts() {
        return inventoryProductRepository.findAll();
    }

    @Override
    public MiningProduction saveProduction(MiningProduction production) {
        return miningProductionRepository.save(production);
    }

    @Override
    public List<MiningProduction> listProduction() {
        return miningProductionRepository.findAll();
    }

    @Override
    public SgsstIncident saveIncident(SgsstIncident incident) {
        return sgsstIncidentRepository.save(incident);
    }

    @Override
    public List<SgsstIncident> listIncidents() {
        return sgsstIncidentRepository.findAll();
    }

    @Override
    public AccountingTransaction saveTransaction(AccountingTransaction transaction) {
        return accountingTransactionRepository.save(transaction);
    }

    @Override
    public List<AccountingTransaction> listTransactions() {
        return accountingTransactionRepository.findAll();
    }

    @Override
    public Map<String, Object> dashboardSummary() {
        BigDecimal income = accountingTransactionRepository.findAll().stream()
            .filter(t -> "INCOME".equals(t.getTransactionType()))
            .map(AccountingTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expense = accountingTransactionRepository.findAll().stream()
            .filter(t -> "EXPENSE".equals(t.getTransactionType()))
            .map(AccountingTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal productionTons = miningProductionRepository.findAll().stream()
            .map(MiningProduction::getToneladas)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new HashMap<>();
        summary.put("income", income);
        summary.put("expense", expense);
        summary.put("balance", income.subtract(expense));
        summary.put("productionTons", productionTons);
        summary.put("incidentsOpen", sgsstIncidentRepository.findAll().stream().filter(i -> "ABIERTO".equals(i.getEstado())).count());
        summary.put("lowStockProducts", inventoryProductRepository.findAll().stream().filter(p -> p.getCantidad() <= p.getStockMinimo()).count());

        return summary;
    }
}
