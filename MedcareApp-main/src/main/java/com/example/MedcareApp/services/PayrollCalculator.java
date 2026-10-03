package com.example.MedcareApp.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PayrollCalculator {
    private PayrollCalculator() {}

    public static BigDecimal componentAmount(
            String calculation,
            BigDecimal fixedAmount,
            BigDecimal percent,
            BigDecimal basic,
            BigDecimal units) {
        BigDecimal amount = switch (calculation) {
            case "FIXED" -> fixedAmount;
            case "PERCENT_OF_BASIC" -> basic.multiply(percent)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            case "PER_UNIT" -> fixedAmount.multiply(units);
            default -> throw new IllegalArgumentException("Unsupported payroll calculation: " + calculation);
        };
        return round(amount);
    }

    public static BigDecimal prorate(BigDecimal amount, long activeDays, long periodDays) {
        if (activeDays < 0 || periodDays <= 0 || activeDays > periodDays) {
            throw new IllegalArgumentException("Payroll proration days are outside the cycle range");
        }
        return round(amount.multiply(BigDecimal.valueOf(activeDays))
                .divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP));
    }

    public static BigDecimal round(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
