package com.example.MedcareApp.Controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.services.HrPayrollService;
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
        when(service.calculatePayroll("run-1"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason));

        var response = new HrPayrollController(service).calculatePayroll("run-1");

        assertEquals(422, response.getStatusCode().value());
        assertEquals(Map.of(
                "message", reason + ". Open Salary → Salary Structures and assign each listed employee "
                        + "an active structure whose effective dates cover the full payroll month, then calculate again.",
                "status", 422), response.getBody());
    }
}
