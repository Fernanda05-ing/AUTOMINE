package com.automine.platform.service;

import com.automine.platform.dto.payroll.PayrollEntryRequest;
import com.automine.platform.dto.payroll.PayrollEntryResponse;

import java.util.List;

public interface PayrollService {
    PayrollEntryResponse createEntry(PayrollEntryRequest request);
    List<PayrollEntryResponse> listEntries();
}
