package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.hrpayroll.Attendance;
import com.example.MedcareApp.Entity.hrpayroll.Employee;
import com.example.MedcareApp.Entity.hrpayroll.LeaveRequest;
import com.example.MedcareApp.Entity.hrpayroll.PayrollRun;
import com.example.MedcareApp.Entity.hrpayroll.SalaryComponent;
import com.example.MedcareApp.Entity.hrpayroll.SalaryStructure;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class HrPayrollServiceTest {
    @Test
    void calculatesAndCombinesPayAcrossMidMonthSalaryChange() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        SalaryComponent basic = new SalaryComponent();
        basic.setCode("BASIC");
        basic.setName("Basic salary");
        basic.setType("EARNING");
        basic.setCalculationType("FIXED");
        when(mongo.findById(basic.getId(), SalaryComponent.class)).thenReturn(basic);
        when(mongo.find(any(Query.class), eq(Attendance.class))).thenReturn(List.of());
        when(mongo.find(any(Query.class), eq(LeaveRequest.class))).thenReturn(List.of());

        Employee employee = new Employee();
        employee.setId("employee-1");
        employee.setEmployeeCode("EMP-1");
        employee.setFirstName("Riya");
        employee.setLastName("Shah");
        employee.setJoiningDate(LocalDate.of(2025, 1, 1));

        SalaryStructure previous = structure(basic, new BigDecimal("31000"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 15));
        SalaryStructure updated = structure(basic, new BigDecimal("62000"),
                LocalDate.of(2026, 10, 16), null);
        List<SalaryStructureTimeline.Segment> segments = SalaryStructureTimeline.segments(
                employee, List.of(previous, updated), YearMonth.of(2026, 10));

        PayrollRun.PayrollItem item = new HrPayrollService(mongo)
                .calculateEmployee(employee, segments, YearMonth.of(2026, 10));

        assertEquals(new BigDecimal("47000.00"), item.getGrossSalary());
        assertEquals(1, item.getEarnings().size());
        assertEquals(new BigDecimal("47000.00"), item.getEarnings().get(0).getAmount());
        assertEquals(31, item.getWorkingDays());
        assertEquals(new BigDecimal("31.00"), item.getPaidDays());
    }

    private SalaryStructure structure(
            SalaryComponent component, BigDecimal amount, LocalDate from, LocalDate to) {
        SalaryStructure structure = new SalaryStructure();
        structure.setEffectiveFrom(from);
        structure.setEffectiveTo(to);
        SalaryStructure.ComponentLine line = new SalaryStructure.ComponentLine();
        line.setComponentId(component.getId());
        line.setAmount(amount);
        structure.setComponents(List.of(line));
        return structure;
    }
}
