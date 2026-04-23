package com.automine.platform.service;

import com.automine.platform.entity.*;

import java.util.List;
import java.util.Map;

public interface EnterpriseModuleService {
    BankDeposit saveDeposit(BankDeposit deposit);
    List<BankDeposit> listDeposits();

    LaborCertificate saveCertificate(LaborCertificate certificate);
    List<LaborCertificate> listCertificates();
    LaborCertificate findCertificateByValidationCode(String validationCode);

    InventoryProduct saveInventoryProduct(InventoryProduct product);
    List<InventoryProduct> listInventoryProducts();

    MiningProduction saveProduction(MiningProduction production);
    List<MiningProduction> listProduction();

    SgsstIncident saveIncident(SgsstIncident incident);
    List<SgsstIncident> listIncidents();

    AccountingTransaction saveTransaction(AccountingTransaction transaction);
    List<AccountingTransaction> listTransactions();

    Map<String, Object> dashboardSummary();
}
