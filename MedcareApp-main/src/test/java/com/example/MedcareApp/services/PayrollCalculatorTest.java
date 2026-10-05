package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PayrollCalculatorTest {
    @Test
    void proratesConfiguredMonthlyAmountsForPaidDays() {
        assertEquals(new BigDecimal("500.00"),
                PayrollCalculator.prorate(new BigDecimal("1000"), new BigDecimal("15"), 30));
        assertEquals(new BigDecimal("750.00"),
                PayrollCalculator.prorate(new BigDecimal("1000"), new BigDecimal("22.5"), 30));
    }

    @Test
    void rejectsInvalidPayrollDayCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> PayrollCalculator.prorate(new BigDecimal("1000"), new BigDecimal("31"), 30));
        assertThrows(IllegalArgumentException.class,
                () -> PayrollCalculator.prorate(new BigDecimal("-1"), BigDecimal.ONE, 30));
    }
}
