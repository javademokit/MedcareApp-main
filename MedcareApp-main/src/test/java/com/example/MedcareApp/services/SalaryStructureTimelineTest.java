package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.MedcareApp.Entity.hrpayroll.Employee;
import com.example.MedcareApp.Entity.hrpayroll.SalaryStructure;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalaryStructureTimelineTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 10);

    @Test
    void splitsPayrollMonthAtMidMonthSalaryChange() {
        Employee employee = employee(LocalDate.of(2025, 1, 1));
        SalaryStructure previous = structure(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 15));
        SalaryStructure updated = structure(LocalDate.of(2026, 10, 16), null);

        List<SalaryStructureTimeline.Segment> segments =
                SalaryStructureTimeline.segments(employee, List.of(updated, previous), MONTH);

        assertEquals(2, segments.size());
        assertEquals(LocalDate.of(2026, 10, 1), segments.get(0).from());
        assertEquals(LocalDate.of(2026, 10, 15), segments.get(0).to());
        assertEquals(previous, segments.get(0).structure());
        assertEquals(LocalDate.of(2026, 10, 16), segments.get(1).from());
        assertEquals(LocalDate.of(2026, 10, 31), segments.get(1).to());
        assertEquals(updated, segments.get(1).structure());
    }

    @Test
    void rejectsCoverageWithAnEffectiveDateGap() {
        Employee employee = employee(LocalDate.of(2025, 1, 1));
        List<SalaryStructureTimeline.Segment> segments = SalaryStructureTimeline.segments(employee, List.of(
                structure(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 14)),
                structure(LocalDate.of(2026, 10, 16), null)), MONTH);

        assertTrue(segments.isEmpty());
    }

    @Test
    void proratesEachSalaryRateAgainstFullPayrollMonth() {
        Employee employee = employee(LocalDate.of(2025, 1, 1));
        List<SalaryStructureTimeline.Segment> segments = SalaryStructureTimeline.segments(employee, List.of(
                structure(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 15)),
                structure(LocalDate.of(2026, 10, 16), null)), MONTH);

        BigDecimal previousRate = PayrollCalculator.prorate(
                new BigDecimal("1000"), segmentDays(segments.get(0)), MONTH.lengthOfMonth());
        BigDecimal updatedRate = PayrollCalculator.prorate(
                new BigDecimal("1200"), segmentDays(segments.get(1)), MONTH.lengthOfMonth());

        assertEquals(new BigDecimal("1103.22"), previousRate.add(updatedRate));
    }

    @Test
    void startsCoverageAtEmployeeJoiningDate() {
        Employee employee = employee(LocalDate.of(2026, 10, 15));
        List<SalaryStructureTimeline.Segment> segments = SalaryStructureTimeline.segments(employee,
                List.of(structure(LocalDate.of(2026, 10, 1), null)), MONTH);

        assertEquals(1, segments.size());
        assertEquals(LocalDate.of(2026, 10, 15), segments.get(0).from());
        assertEquals(LocalDate.of(2026, 10, 31), segments.get(0).to());
    }

    private Employee employee(LocalDate joiningDate) {
        Employee employee = new Employee();
        employee.setJoiningDate(joiningDate);
        return employee;
    }

    private SalaryStructure structure(LocalDate from, LocalDate to) {
        SalaryStructure structure = new SalaryStructure();
        structure.setEffectiveFrom(from);
        structure.setEffectiveTo(to);
        return structure;
    }

    private BigDecimal segmentDays(SalaryStructureTimeline.Segment segment) {
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(segment.from(), segment.to()) + 1);
    }
}
