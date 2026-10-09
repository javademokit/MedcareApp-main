package com.example.MedcareApp.Controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.services.HrPayrollService;
import java.security.Principal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class HrPayrollControllerTest {
    @Test
    void returnsPayrollCalculationReasonIn422Response() {
        HrPayrollService service = mock(HrPayrollService.class);
        String reason = "Payroll cannot be calculated: DT-100 is missing an active salary structure "
                + "covering the full payroll month (2026-10)";
        String actor = "payroll-manager";
        when(service.calculatePayroll("run-1", actor))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason));

        Principal principal = () -> actor;
        var response = new HrPayrollController(service).calculatePayroll("run-1", principal);

        assertEquals(422, response.getStatusCode().value());
        assertEquals(Map.of(
                "message", "Payroll was not calculated. These active employee(s) need an active salary structure "
                        + "covering the full payroll month: DT-100 is missing an active salary structure "
                        + "covering the full payroll month (2026-10). Open Salary → Salary Structures, assign each "
                        + "employee an active structure whose effective dates cover the entire month, then calculate "
                        + "again. After calculation succeeds, the CRM team can review and approve the payroll run.",
                "status", 422), response.getBody());
    }
}
