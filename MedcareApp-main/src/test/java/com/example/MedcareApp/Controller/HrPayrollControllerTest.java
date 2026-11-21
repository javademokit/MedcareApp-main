package com.example.MedcareApp.Controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.hrpayroll.PayrollRun;
import com.example.MedcareApp.services.HrPayrollService;
import java.security.Principal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
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

    @Test
    void allowsHospitalAdminToApproveTheirOwnPayrollRun() {
        HrPayrollService service = mock(HrPayrollService.class);
        String actor = "admin@example.test";
        PayrollRun approved = new PayrollRun();
        approved.setId("run-1");
        approved.setStatus("APPROVED");
        when(service.approvePayroll("run-1", actor, true)).thenReturn(approved);

        Principal principal = () -> actor;
        var authentication = new TestingAuthenticationToken(actor, "password", "ROLE_HOSPITAL_ADMIN");
        PayrollRun response = new HrPayrollController(service).approvePayroll("run-1", principal, authentication);

        assertEquals("APPROVED", response.getStatus());
        verify(service).approvePayroll("run-1", actor, true);
    }

    @Test
    void doesNotAllowFinanceRoleToBypassPayrollMakerApprovalSeparation() {
        HrPayrollService service = mock(HrPayrollService.class);
        String actor = "finance@example.test";
        PayrollRun pending = new PayrollRun();
        pending.setId("run-1");
        pending.setStatus("PENDING_APPROVAL");
        when(service.approvePayroll("run-1", actor, false)).thenReturn(pending);

        Principal principal = () -> actor;
        var authentication = new TestingAuthenticationToken(actor, "password", "ROLE_FINANCE");
        PayrollRun response = new HrPayrollController(service).approvePayroll("run-1", principal, authentication);

        assertEquals("PENDING_APPROVAL", response.getStatus());
        verify(service).approvePayroll("run-1", actor, false);
    }
}
