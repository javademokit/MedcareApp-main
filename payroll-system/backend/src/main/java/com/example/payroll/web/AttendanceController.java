package com.example.payroll.web;

import com.example.payroll.dto.Dtos.AttendanceMonth;
import com.example.payroll.dto.Dtos.AttendanceUpdate;
import com.example.payroll.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService service;

    @GetMapping public AttendanceMonth month(@RequestParam String month) { return service.month(month); }
    @PutMapping public ResponseEntity<Void> update(@RequestBody AttendanceUpdate u) { service.update(u); return ResponseEntity.noContent().build(); }
}
