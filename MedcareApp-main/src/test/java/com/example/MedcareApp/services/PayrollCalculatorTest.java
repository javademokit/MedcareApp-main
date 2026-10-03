package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PayrollCalculatorTest {
    @Test
    void calculatesConfigurableComponentTypesWithLineRounding() {
        assertEquals(new BigDecimal("125.56"), PayrollCalculator.componentAmount(
                "PERCENT_OF_BASIC", BigDecimal.ZERO, new BigDecimal("12.5"),
                new BigDecimal("1004.47"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("56.25"), PayrollCalculator.componentAmount(
                "PER_UNIT", new BigDecimal("18.75"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("3")));
        assertEquals(new BigDecimal("90.00"), PayrollCalculator.componentAmount(
                "FIXED", new BigDecimal("90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void proratesMidCycleSalaryAndRejectsInvalidDayRanges() {
        assertEquals(new BigDecimal("500.00"),
                PayrollCalculator.prorate(new BigDecimal("1000"), 15, 30));
        assertThrows(IllegalArgumentException.class,
                () -> PayrollCalculator.prorate(new BigDecimal("1000"), 31, 30));
    }

    @Test
    void generatesAValidPdfPayslipDocument() {
        byte[] pdf = PayslipPdfGenerator.generate(java.util.List.of("MEDCARE HOSPITAL", "Net pay: INR 1200.00"));

        assertEquals('%', pdf[0]);
        assertEquals('P', pdf[1]);
        assertEquals('D', pdf[2]);
        assertEquals('F', pdf[3]);
        assertEquals('-', pdf[4]);
        assertEquals('1', pdf[5]);
    }

    @Test
    void paginatesLargePayslipsWithoutDroppingRows() {
        byte[] pdf = PayslipPdfGenerator.generate(
                java.util.stream.IntStream.range(0, 75).mapToObj(index -> "Earning " + index).toList());
        String content = new String(pdf, java.nio.charset.StandardCharsets.US_ASCII);

        assertTrue(content.contains("/Count 2"));
        assertTrue(content.contains("(Earning 74) Tj"));
    }

    @Test
    void staffIdentifiersUseRequestedPrefixes() {
        assertTrue(StaffIdentifierGenerator.generate("DT").matches("DT-[A-F0-9]{8}"));
        assertTrue(StaffIdentifierGenerator.generate("NS").matches("NS-[A-F0-9]{8}"));
        assertTrue(StaffIdentifierGenerator.generate("PT").matches("PT-[A-F0-9]{8}"));
    }

    @Test
    void evaluatesBoundedPayrollFormulasWithPrecedenceAndVariables() {
        assertEquals(new BigDecimal("300.00"), PayrollFormula.evaluate(
                "(BASIC * 0.10) + (UNITS * 25)", new BigDecimal("2000"), new BigDecimal("4")));
        assertEquals(new BigDecimal("14.00"), PayrollFormula.evaluate(
                "2 + 3 * 4", BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsUnsafeOrInvalidPayrollFormulas() {
        assertThrows(IllegalArgumentException.class,
                () -> PayrollFormula.evaluate("BASIC / 0", BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> PayrollFormula.evaluate("BASIC + System.exit(1)", BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> PayrollFormula.evaluate("(BASIC + 1", BigDecimal.ONE, BigDecimal.ONE));
    }
}
