package com.example.payroll.service;

import com.example.payroll.model.RunMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.YearMonth;
import java.time.ZoneId;

/** Runs payroll for the current month at 6:00 pm on the last day of every month (if auto-run is switched on). */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollScheduler {
    private final PayrollService payroll;
    private final SettingsService settings;
    @Value("${payroll.timezone}")
    private String timezone;

    @Scheduled(cron = "0 0 18 L * *", zone = "${payroll.timezone}")
    public void monthEndRun() {
        if (!settings.autoRun()) {
            log.info("Auto payroll is switched off, skipping");
            return;
        }
        YearMonth month = YearMonth.now(ZoneId.of(timezone));
        try {
            payroll.run(month.toString(), RunMode.AUTO);
            log.info("Auto payroll processed for {}", month);
        } catch (ResponseStatusException e) {
            log.warn("Auto payroll for {} skipped: {}", month, e.getReason());
        }
    }
}
