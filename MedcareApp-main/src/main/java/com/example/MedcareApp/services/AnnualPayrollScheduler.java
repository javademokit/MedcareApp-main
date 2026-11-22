package com.example.MedcareApp.services;

import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@EnableScheduling
public class AnnualPayrollScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnualPayrollScheduler.class);
    private static final String SYSTEM_ACTOR = "system:annual-payroll-scheduler";
    private static final ZoneId PAYROLL_ZONE = ZoneId.of("Asia/Kolkata");

    private final HrPayrollService payrollService;

    public AnnualPayrollScheduler(HrPayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createCurrentYearDraftRuns() {
        createDraftRuns(Year.now(PAYROLL_ZONE).getValue());
    }

    @Scheduled(cron = "0 0 2 1 1 *", zone = "Asia/Kolkata")
    public void createNextCalendarYearDraftRuns() {
        createDraftRuns(Year.now(PAYROLL_ZONE).getValue());
    }

    @Scheduled(cron = "0 0 2 5 * *", zone = "Asia/Kolkata")
    public void calculatePreviousMonthDraft() {
        YearMonth payrollMonth = YearMonth.now(PAYROLL_ZONE).minusMonths(1);
        try {
            var result = payrollService.calculatePayrollIfDraft(payrollMonth.toString(), SYSTEM_ACTOR);
            if (result == null) {
                LOGGER.warn("No payroll run exists for {} at the scheduled calculation time", payrollMonth);
            } else if ("CALCULATED".equals(result.getStatus())) {
                LOGGER.info("Automatically calculated draft payroll for {}", payrollMonth);
            } else {
                LOGGER.info("Automatic payroll calculation skipped for {} because its status is {}",
                        payrollMonth, result.getStatus());
            }
        } catch (ResponseStatusException | IllegalArgumentException exception) {
            if (exception instanceof ResponseStatusException response
                    && response.getStatusCode() != HttpStatus.UNPROCESSABLE_ENTITY
                    && response.getStatusCode() != HttpStatus.CONFLICT
                    && response.getStatusCode() != HttpStatus.BAD_REQUEST) {
                throw exception;
            }
            String reason = exception instanceof ResponseStatusException response
                    ? response.getReason() : exception.getMessage();
            LOGGER.error("Automatic payroll calculation failed for {}: {}", payrollMonth, reason, exception);
        }
    }

    private void createDraftRuns(int year) {
        var created = payrollService.createMissingPayrollRunsForYear(year, SYSTEM_ACTOR);
        LOGGER.info("Ensured calendar-year payroll schedule for {} ({} new draft runs)", year, created.size());
    }
}
