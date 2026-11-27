package com.example.payroll.service;

import com.example.payroll.dto.Dtos.*;
import com.example.payroll.model.*;
import com.example.payroll.repo.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollService {
    private final EmployeeRepository employees;
    private final AttendanceRepository attendance;
    private final PayrollRunRepository runs;
    private final PayslipSnapshotRepository snapshots;
    private final PayrollCalculator calculator;
    private final ObjectMapper mapper;

    /** Saved figures if the month was processed, otherwise a live calculation from current attendance and salaries. */
    public PayrollMonth get(String month) {
        YearMonth ym = parse(month);
        Optional<PayrollRun> run = runs.findByPayMonth(ym.toString());
        List<PayrollRow> rows = run.isPresent()
                ? read(run.get()).stream().map(row -> withWeeklyOffDays(row, ym)).toList()
                : compute(ym);
        return toMonth(ym, run.orElse(null), rows);
    }

    public PayrollMonth run(String month, RunMode mode) {
        YearMonth ym = parse(month);
        if (runs.findByPayMonth(ym.toString()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payroll for " + ym + " is already processed");
        List<PayrollRow> rows = compute(ym);
        PayrollRun run = new PayrollRun();
        run.setPayMonth(ym.toString());
        run.setRunMode(mode);
        run.setRunAt(LocalDateTime.now());
        runs.save(run);
        for (PayrollRow r : rows) {
            snapshots.save(new PayslipSnapshot(null, run.getId(), r.employeeId(), toJson(r)));
        }
        return toMonth(ym, run, rows);
    }

    public List<RunDto> history() {
        return runs.findAllByOrderByPayMonthDesc().stream()
                .map(r -> new RunDto(r.getPayMonth(), r.getRunMode().name(), r.getRunAt(),
                        read(r).stream().map(PayrollRow::net).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
    }

    private List<PayrollRow> compute(YearMonth ym) {
        Map<Long, Map<LocalDate, AttendanceStatus>> byEmp = attendance
                .findByWorkDateBetween(ym.atDay(1), ym.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(Attendance::getEmployeeId,
                        Collectors.toMap(Attendance::getWorkDate, Attendance::getStatus)));
        return employees.findAllByOrderByIdAsc().stream()
                .map(e -> calculator.calculate(e, ym, byEmp.getOrDefault(e.getId(), Map.of())))
                .toList();
    }

    private static PayrollRow withWeeklyOffDays(PayrollRow row, YearMonth ym) {
        if (row.weeklyOffDays() != null) return row;
        long weekendCount = ym.atDay(1).datesUntil(ym.atEndOfMonth().plusDays(1))
                .filter(date -> date.getDayOfWeek() == DayOfWeek.SATURDAY
                        || date.getDayOfWeek() == DayOfWeek.SUNDAY)
                .count();
        return new PayrollRow(row.employeeId(), row.code(), row.name(), row.department(), row.role(), row.bank(),
                row.structureName(), row.monthlyGross(), row.totalDays(), row.paidDays(), row.lopDays(),
                BigDecimal.valueOf(weekendCount), row.earnings(), row.deductions(), row.gross(),
                row.totalDeductions(), row.net(), row.overAllocated());
    }

    private List<PayrollRow> read(PayrollRun run) {
        return snapshots.findByRunId(run.getId()).stream()
                .map(s -> fromJson(s.getPayload()))
                .sorted(Comparator.comparing(PayrollRow::employeeId))
                .toList();
    }

    private PayrollMonth toMonth(YearMonth ym, PayrollRun run, List<PayrollRow> rows) {
        BigDecimal gross = rows.stream().map(PayrollRow::gross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ded = rows.stream().map(PayrollRow::totalDeductions).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayrollMonth(ym.toString(), run != null,
                run == null ? null : run.getRunMode().name(), run == null ? null : run.getRunAt(),
                gross, ded, gross.subtract(ded), rows);
    }

    private YearMonth parse(String month) {
        try { return YearMonth.parse(month); }
        catch (DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must look like 2026-11"); }
    }

    private String toJson(PayrollRow r) {
        try { return mapper.writeValueAsString(r); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private PayrollRow fromJson(String json) {
        try { return mapper.readValue(json, PayrollRow.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
