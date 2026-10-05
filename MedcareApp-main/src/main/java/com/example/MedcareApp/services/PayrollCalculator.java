package com.example.MedcareApp.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PayrollCalculator {
    private PayrollCalculator() {}

    public static BigDecimal prorate(BigDecimal monthlyAmount, BigDecimal paidDays, int periodDays) {
        if (monthlyAmount == null || monthlyAmount.signum() < 0
                || paidDays == null || paidDays.signum() < 0 || periodDays < 0) {
            throw new IllegalArgumentException("Payroll amounts and days must be non-negative");
        }
        if (periodDays == 0) {
            if (paidDays.signum() != 0) throw new IllegalArgumentException("Paid days require period days");
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (paidDays.compareTo(BigDecimal.valueOf(periodDays)) > 0) {
            throw new IllegalArgumentException("Paid days cannot exceed period days");
        }
        return round(monthlyAmount.multiply(paidDays)
                .divide(BigDecimal.valueOf(periodDays), 8, RoundingMode.HALF_UP));
    }

    private static BigDecimal round(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
