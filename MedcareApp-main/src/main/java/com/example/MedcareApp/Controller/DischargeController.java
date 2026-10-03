package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.DischargeCase;
import com.example.MedcareApp.services.DischargeService;
import com.example.MedcareApp.web.CreateDischargeRequest;
import com.example.MedcareApp.web.DischargePaymentRequest;
import com.example.MedcareApp.web.InsuranceClaimRequest;
import com.example.MedcareApp.web.InsuranceDecisionRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discharges")
@RequiredArgsConstructor
public class DischargeController {
    private final DischargeService dischargeService;

    @GetMapping
    public List<DischargeCase> getCases() {
        return dischargeService.getCases();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DischargeCase createCase(@Valid @RequestBody CreateDischargeRequest request, Principal principal) {
        return dischargeService.createCase(request, principal.getName());
    }

    @PostMapping("/{id}/clinical-approval")
    public DischargeCase approveClinicalClearance(@PathVariable String id, Principal principal) {
        return dischargeService.approveClinicalClearance(id, principal.getName());
    }

    @PostMapping("/{id}/insurance-claims")
    public DischargeCase submitInsuranceClaim(@PathVariable String id, @Valid @RequestBody InsuranceClaimRequest request) {
        return dischargeService.submitInsuranceClaim(id, request);
    }

    @PatchMapping("/{id}/insurance-claims/decision")
    public DischargeCase recordInsuranceDecision(
            @PathVariable String id,
            @Valid @RequestBody InsuranceDecisionRequest request,
            Principal principal) {
        return dischargeService.recordInsuranceDecision(id, request, principal.getName());
    }

    @PostMapping("/{id}/payments")
    public DischargeCase recordPayment(@PathVariable String id, @Valid @RequestBody DischargePaymentRequest request) {
        return dischargeService.recordPayment(id, request);
    }

    @PostMapping("/{id}/complete")
    public DischargeCase completeDischarge(@PathVariable String id, Principal principal) {
        return dischargeService.completeDischarge(id, principal.getName());
    }
}
