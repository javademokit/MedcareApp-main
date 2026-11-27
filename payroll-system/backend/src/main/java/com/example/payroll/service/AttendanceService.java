package com.example.payroll.service;

import com.example.payroll.dto.Dtos.*;
import com.example.payroll.model.Attendance;
import com.example.payroll.model.AttendanceStatus;
import com.example.payroll.repo.AttendanceRepository;
import com.example.payroll.repo.EmployeeRepository;
import com.example.payroll.repo.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {
    private final AttendanceRepository attendance;
    private final EmployeeRepository employees;
    private final PayrollRunRepository runs;

    public AttendanceMonth month(String month) {
        YearMonth ym = YearMonth.parse(month);
        Map<Long, Map<LocalDate, AttendanceStatus>> byEmp = attendance
                .findByWorkDateBetween(ym.atDay(1), ym.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(Attendance::getEmployeeId,
                        Collectors.toMap(Attendance::getWorkDate, Attendance::getStatus)));
        List<EmployeeAttendance> rows = new ArrayList<>();
        employees.findAllByOrderByIdAsc().forEach(e -> {
            Map<LocalDate, AttendanceStatus> marked = byEmp.getOrDefault(e.getId(), Map.of());
            List<String> days = new ArrayList<>();
            for (int d = 1; d <= ym.lengthOfMonth(); d++) {
                LocalDate date = ym.atDay(d);
                days.add(isWeekend(date) ? AttendanceStatus.WEEKLY_OFF.code()
                        : marked.getOrDefault(date, AttendanceStatus.PRESENT).code());
            }
            rows.add(new EmployeeAttendance(e.getId(), e.getCode(), e.getName(),
                    e.getDepartment(), e.getRole(), days));
        });
        return new AttendanceMonth(month, runs.findByPayMonth(month).isPresent(), ym.lengthOfMonth(), rows);
    }

    public void update(AttendanceUpdate u) {
        YearMonth ym = YearMonth.from(u.date());
        if (runs.findByPayMonth(ym.toString()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payroll for " + ym + " is processed. Attendance is locked.");
        if (isWeekend(u.date()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weekends are weekly offs");
        if (!employees.existsById(u.employeeId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
        var existing = attendance.findByEmployeeIdAndWorkDate(u.employeeId(), u.date());
        if (u.status() == AttendanceStatus.PRESENT || u.status() == AttendanceStatus.WEEKLY_OFF) {
            existing.ifPresent(attendance::delete);           // present is the default, so no row needed
            return;
        }
        Attendance a = existing.orElseGet(Attendance::new);
        a.setEmployeeId(u.employeeId());
        a.setWorkDate(u.date());
        a.setStatus(u.status());
        attendance.save(a);
    }

    private static boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
