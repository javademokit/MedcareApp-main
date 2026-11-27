package com.example.payroll.config;

import com.example.payroll.model.*;
import com.example.payroll.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Loads demo structures, employees and a few absences on first start (only when the database is empty). */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final StructureRepository structures;
    private final EmployeeRepository employees;
    private final AttendanceRepository attendance;

    @Override
    public void run(String... args) {
        if (employees.count() > 0) return;

        Structure std = structure("Standard", 40,
                comp("House rent allowance", ComponentKind.EARNING, CalcType.PCT_BASIC, 50, null),
                comp("Special allowance", ComponentKind.EARNING, CalcType.BALANCE, 0, null),
                comp("Provident fund", ComponentKind.DEDUCTION, CalcType.PCT_BASIC, 12, 15000),
                comp("Professional tax", ComponentKind.DEDUCTION, CalcType.FIXED, 200, null),
                comp("Income tax (TDS)", ComponentKind.DEDUCTION, CalcType.PCT_GROSS, 5, null));
        Structure senior = structure("Senior / Manager", 45,
                comp("House rent allowance", ComponentKind.EARNING, CalcType.PCT_BASIC, 50, null),
                comp("Conveyance allowance", ComponentKind.EARNING, CalcType.FIXED, 3200, null),
                comp("Special allowance", ComponentKind.EARNING, CalcType.BALANCE, 0, null),
                comp("Provident fund", ComponentKind.DEDUCTION, CalcType.PCT_BASIC, 12, 15000),
                comp("Professional tax", ComponentKind.DEDUCTION, CalcType.FIXED, 200, null),
                comp("Income tax (TDS)", ComponentKind.DEDUCTION, CalcType.PCT_GROSS, 10, null));
        Structure assoc = structure("Associate", 50,
                comp("House rent allowance", ComponentKind.EARNING, CalcType.PCT_BASIC, 40, null),
                comp("Special allowance", ComponentKind.EARNING, CalcType.BALANCE, 0, null),
                comp("Provident fund", ComponentKind.DEDUCTION, CalcType.PCT_BASIC, 12, 15000),
                comp("Professional tax", ComponentKind.DEDUCTION, CalcType.FIXED, 200, null));

        List<Employee> list = new ArrayList<>();
        list.add(emp("EMP-000001", "Ananya Iyer", "Engineering", "Senior Engineer", 145000, "HDFC ••4821", std));
        list.add(emp("EMP-000002", "Karthik Raman", "Engineering", "Tech Lead", 210000, "ICICI ••9034", senior));
        list.add(emp("EMP-000003", "Meera Nair", "Product", "Product Manager", 165000, "SBI ••1170", senior));
        list.add(emp("EMP-000004", "Rohan Mehta", "Sales", "Account Executive", 95000, "Axis ••6652", std));
        list.add(emp("EMP-000005", "Divya Krishnan", "HR", "HR Manager", 120000, "HDFC ••3309", std));
        list.add(emp("EMP-000006", "Imran Shaikh", "Finance", "Accountant", 102000, "Kotak ••7745", std));
        list.add(emp("EMP-000007", "Sneha Patil", "Support", "Support Lead", 68000, "SBI ••2286", assoc));
        list.add(emp("EMP-000008", "Arjun Reddy", "Sales", "Sales Manager", 155000, "ICICI ••5518", senior));
        list.add(emp("EMP-000009", "Lakshmi Sundar", "Engineering", "QA Engineer", 106000, "Axis ••8801", std));
        list.add(emp("EMP-000010", "Vikram Joshi", "Support", "Support Associate", 46000, "HDFC ••1953", assoc));
        employees.saveAll(list);

        // Demo absences / half days / leaves for Oct and Nov 2026 (deterministic pattern)
        int[][] months = {{2026, 10}, {2026, 11}};
        List<Attendance> rows = new ArrayList<>();
        for (int mi = 0; mi < months.length; mi++) {
            YearMonth ym = YearMonth.of(months[mi][0], months[mi][1]);
            for (int ix = 0; ix < list.size(); ix++) {
                for (int d = 1; d <= ym.lengthOfMonth(); d++) {
                    LocalDate date = ym.atDay(d);
                    if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
                    int s = d * 7 + ix * 13 + mi * 5;
                    AttendanceStatus st = s % 17 == 0 ? AttendanceStatus.ABSENT
                            : s % 11 == 3 ? AttendanceStatus.PAID_LEAVE
                            : s % 19 == 4 ? AttendanceStatus.HALF_DAY : null;
                    if (st == null) continue;
                    Attendance a = new Attendance();
                    a.setEmployeeId(list.get(ix).getId());
                    a.setWorkDate(date);
                    a.setStatus(st);
                    rows.add(a);
                }
            }
        }
        attendance.saveAll(rows);
    }

    private Structure structure(String name, int basicPct, StructureComponent... comps) {
        Structure s = new Structure();
        s.setName(name);
        s.setBasicPct(BigDecimal.valueOf(basicPct));
        s.getComponents().addAll(List.of(comps));
        return structures.save(s);
    }

    private static StructureComponent comp(String name, ComponentKind kind, CalcType type, int value, Integer cap) {
        StructureComponent c = new StructureComponent();
        c.setName(name);
        c.setKind(kind);
        c.setCalcType(type);
        c.setCalcValue(BigDecimal.valueOf(value));
        c.setCapAmount(cap == null ? null : BigDecimal.valueOf(cap));
        return c;
    }

    private static Employee emp(String code, String name, String dept, String role, int gross, String bank, Structure s) {
        Employee e = new Employee();
        e.setCode(code); e.setName(name); e.setDepartment(dept); e.setRole(role);
        e.setMonthlyGross(BigDecimal.valueOf(gross)); e.setBank(bank); e.setStructure(s);
        return e;
    }
}
