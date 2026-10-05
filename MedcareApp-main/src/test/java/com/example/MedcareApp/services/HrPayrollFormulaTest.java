package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HrPayrollFormulaTest {
    @Test
    void evaluatesConfiguredPayrollExpressionsWithPrecedenceAndVariables() {
        assertEquals(new BigDecimal("300.00"), PayrollFormula.evaluate(
                "BASIC * 0.30", Map.of("BASIC", new BigDecimal("1000"))));
        assertEquals(new BigDecimal("150.00"), PayrollFormula.evaluate(
                "(BASIC + 200) / 4", Map.of("BASIC", new BigDecimal("400"))));
        assertEquals(new BigDecimal("18.00"), PayrollFormula.evaluate(
                "OVERTIME_HOURS * 1.5", Map.of("OVERTIME_HOURS", new BigDecimal("12"))));
    }

    @Test
    void rejectsUnknownVariablesMalformedExpressionsAndDivisionByZero() {
        assertThrows(IllegalArgumentException.class,
                () -> PayrollFormula.evaluate("SYSTEM_EXIT", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> PayrollFormula.evaluate("BASIC / 0", Map.of("BASIC", BigDecimal.TEN)));
        assertThrows(IllegalArgumentException.class,
                () -> PayrollFormula.evaluate("(BASIC + 1", Map.of("BASIC", BigDecimal.TEN)));
    }
}
