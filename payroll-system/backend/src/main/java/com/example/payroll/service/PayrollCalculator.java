package com.example.payroll.service;

import com.example.payroll.dto.Dtos.LineDto;
import com.example.payroll.dto.Dtos.PayrollRow;
import com.example.payroll.model.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Salary = employee's monthly gross split by the assigned salary structure,
 * then prorated by paid days (present + paid leave + weekly offs + 0.5 per half day) / days in month.
 */
@Component
public class PayrollCalculator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    public PayrollRow calculate(Employee e, YearMonth ym, Map<LocalDate, AttendanceStatus> marked) {
        int n = ym.lengthOfMonth();
        BigDecimal paid = BigDecimal.ZERO, lop = BigDecimal.ZERO, weeklyOff = BigDecimal.ZERO;
        for (int d = 1; d <= n; d++) {
            LocalDate date = ym.atDay(d);
            DayOfWeek w = date.getDayOfWeek();
            boolean isWeeklyOff = w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY;
            AttendanceStatus st = isWeeklyOff
                    ? AttendanceStatus.WEEKLY_OFF
                    : marked.getOrDefault(date, AttendanceStatus.PRESENT);
            if (isWeeklyOff) weeklyOff = weeklyOff.add(BigDecimal.ONE);
            switch (st) {
                case ABSENT -> lop = lop.add(BigDecimal.ONE);
                case HALF_DAY -> { paid = paid.add(HALF); lop = lop.add(HALF); }
                default -> paid = paid.add(BigDecimal.ONE);
            }
        }
        BigDecimal f = paid.divide(BigDecimal.valueOf(n), 10, RM);
        Structure s = e.getStructure();
        BigDecimal g = e.getMonthlyGross();
        BigDecimal basic = g.multiply(s.getBasicPct()).divide(HUNDRED, 10, RM);

        List<LineDto> earnings = new ArrayList<>();
        earnings.add(line("Basic", basic.multiply(f)));
        BigDecimal fixed = basic;
        StructureComponent balance = null;
        for (StructureComponent c : s.getComponents()) {
            if (c.getKind() != ComponentKind.EARNING) continue;
            if (c.getCalcType() == CalcType.BALANCE) { if (balance == null) balance = c; continue; }
            BigDecimal v = c.getCalcValue() == null ? BigDecimal.ZERO : c.getCalcValue();
            BigDecimal amt = switch (c.getCalcType()) {
                case PCT_BASIC -> basic.multiply(v).divide(HUNDRED, 10, RM);
                case PCT_GROSS -> g.multiply(v).divide(HUNDRED, 10, RM);
                default -> v;
            };
            fixed = fixed.add(amt);
            earnings.add(line(c.getName(), amt.multiply(f)));
        }
        if (balance != null) {
            earnings.add(line(balance.getName(), g.subtract(fixed).max(BigDecimal.ZERO).multiply(f)));
        }
        boolean over = g.compareTo(fixed) < 0;
        BigDecimal gross = sum(earnings);

        List<LineDto> deductions = new ArrayList<>();
        for (StructureComponent c : s.getComponents()) {
            if (c.getKind() != ComponentKind.DEDUCTION) continue;
            BigDecimal v = c.getCalcValue() == null ? BigDecimal.ZERO : c.getCalcValue();
            BigDecimal amt = switch (c.getCalcType()) {
                case FIXED -> f.signum() > 0 ? v : BigDecimal.ZERO;
                case PCT_BASIC -> {
                    BigDecimal cap = c.getCapAmount();
                    BigDecimal base = (cap != null && cap.signum() > 0) ? basic.min(cap) : basic;
                    yield base.multiply(f).multiply(v).divide(HUNDRED, 10, RM);
                }
                case PCT_GROSS -> gross.multiply(v).divide(HUNDRED, 10, RM);
                default -> BigDecimal.ZERO;
            };
            deductions.add(line(c.getName(), amt));
        }
        BigDecimal totalDed = sum(deductions);

        return new PayrollRow(e.getId(), e.getCode(), e.getName(), e.getDepartment(), e.getRole(), e.getBank(),
                s.getName(), g, n, paid.setScale(1, RM), lop.setScale(1, RM), weeklyOff.setScale(1, RM),
                earnings, deductions, gross, totalDed, gross.subtract(totalDed), over);
    }

    private static LineDto line(String name, BigDecimal amt) {
        return new LineDto(name, amt.setScale(2, RM));
    }

    private static BigDecimal sum(List<LineDto> lines) {
        return lines.stream().map(LineDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
