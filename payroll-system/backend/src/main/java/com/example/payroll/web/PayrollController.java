package com.example.payroll.web;

import com.example.payroll.dto.Dtos.*;
import com.example.payroll.model.RunMode;
import com.example.payroll.service.PayrollService;
import com.example.payroll.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PayrollController {
    private final PayrollService payroll;
    private final SettingsService settings;

    @GetMapping("/payroll/runs") public List<RunDto> runs() { return payroll.history(); }
    @GetMapping("/payroll/{month}") public PayrollMonth get(@PathVariable String month) { return payroll.get(month); }
    @PostMapping("/payroll/{month}/run") public PayrollMonth run(@PathVariable String month) { return payroll.run(month, RunMode.MANUAL); }

    @GetMapping("/settings/auto-run") public Toggle autoRun() { return new Toggle(settings.autoRun()); }
    @PutMapping("/settings/auto-run") public Toggle setAutoRun(@RequestBody Toggle t) { settings.setAutoRun(t.enabled()); return t; }
}
