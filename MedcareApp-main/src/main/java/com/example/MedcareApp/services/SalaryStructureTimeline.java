package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.hrpayroll.Employee;
import com.example.MedcareApp.Entity.hrpayroll.SalaryStructure;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SalaryStructureTimeline {
    private SalaryStructureTimeline() {}

    static List<Segment> segments(Employee employee, List<SalaryStructure> structures, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDate joiningDate = employee.getJoiningDate();
        LocalDate nextDate = joiningDate == null || joiningDate.isBefore(monthStart) ? monthStart : joiningDate;
        if (nextDate.isAfter(monthEnd)) return List.of();
        if (structures.stream().anyMatch(structure -> structure.getEffectiveFrom() == null)) {
            throw new IllegalArgumentException("Salary structure effective date is required");
        }

        List<Segment> segments = new ArrayList<>();
        for (SalaryStructure structure : structures.stream()
                .sorted(Comparator.comparing(SalaryStructure::getEffectiveFrom)).toList()) {
            LocalDate effectiveFrom = structure.getEffectiveFrom();
            LocalDate effectiveTo = structure.getEffectiveTo() == null
                    || structure.getEffectiveTo().isAfter(monthEnd) ? monthEnd : structure.getEffectiveTo();
            if (effectiveTo.isBefore(nextDate)) continue;
            if (effectiveFrom.isAfter(nextDate)) return List.of();

            segments.add(new Segment(structure, nextDate, effectiveTo));
            nextDate = effectiveTo.plusDays(1);
            if (nextDate.isAfter(monthEnd)) return List.copyOf(segments);
        }
        return List.of();
    }

    record Segment(SalaryStructure structure, LocalDate from, LocalDate to) {}
}
