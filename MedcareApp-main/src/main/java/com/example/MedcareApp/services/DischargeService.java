package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.DischargeCase;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.billing.DischargePayment;
import com.example.MedcareApp.Entity.billing.InsuranceClaim;
import com.example.MedcareApp.Interafce.DischargeCaseRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.web.CreateDischargeRequest;
import com.example.MedcareApp.web.DischargePaymentRequest;
import com.example.MedcareApp.web.InsuranceClaimRequest;
import com.example.MedcareApp.web.InsuranceDecisionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DischargeService {
    private static final List<String> CLAIM_DECISIONS = List.of("APPROVED", "PARTIALLY_APPROVED", "DENIED");
    private final DischargeCaseRepository dischargeRepository;
    private final PatientRepository patientRepository;
    private final NursingService nursingService;

    public List<DischargeCase> getCases() {
        return dischargeRepository.findAllByOrderByCreatedAtDesc();
    }

    public DischargeCase createCase(CreateDischargeRequest request, String createdBy) {
        List<Patient> patients = patientRepository.findAllByPatientId(request.getPatientId());
        if (patients.size() != 1) {
            throw new ResponseStatusException(patients.isEmpty() ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                    patients.isEmpty() ? "Patient not found" : "Patient number is not unique");
        }
        Patient patient = patients.get(0);
        if (patient.getPatientDischargedate() != null && !patient.getPatientDischargedate().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient is already discharged");
        }
        if (patient.getPatientAdmitdate() == null || patient.getPatientAdmitdate().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient has no admission record");
        }
        if (!dischargeRepository.findAllByPatientIdAndStatusNot(request.getPatientId(), "DISCHARGED").isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active discharge case already exists");
        }
        DischargeCase dischargeCase = new DischargeCase();
        dischargeCase.setPatientId(patient.getPatientId());
        dischargeCase.setPatientName(patient.getPatientName());
        dischargeCase.setAdmissionDate(patient.getPatientAdmitdate());
        dischargeCase.setDiagnosis(request.getDiagnosis());
        dischargeCase.setDischargeSummary(request.getDischargeSummary());
        dischargeCase.setAttendingDoctor(request.getAttendingDoctor());
        dischargeCase.setInvoiceTotal(request.getInvoiceTotal());
        dischargeCase.setStatus("IN_PROGRESS");
        dischargeCase.setCreatedAt(Instant.now());
        dischargeCase.setUpdatedAt(Instant.now());
        return dischargeRepository.save(dischargeCase);
    }

    public DischargeCase approveClinicalClearance(String id, String approvedBy) {
        DischargeCase dischargeCase = getCase(id);
        ensureNotDischarged(dischargeCase);
        dischargeCase.setClinicalStatus("APPROVED");
        dischargeCase.setClinicalApprovedBy(approvedBy);
        dischargeCase.setClinicalApprovedAt(Instant.now());
        return saveAndRefreshStatus(dischargeCase);
    }

    public DischargeCase submitInsuranceClaim(String id, InsuranceClaimRequest request) {
        DischargeCase dischargeCase = getCase(id);
        ensureNotDischarged(dischargeCase);
        if (request.getRequestedAmount().compareTo(dischargeCase.getInvoiceTotal()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Claim amount cannot exceed the invoice total");
        }
        InsuranceClaim claim = new InsuranceClaim();
        claim.setProvider(request.getProvider());
        claim.setPolicyLastFour(request.getPolicyLastFour());
        claim.setRequestedAmount(request.getRequestedAmount());
        claim.setApprovedAmount(BigDecimal.ZERO);
        claim.setStatus("SUBMITTED");
        claim.setSubmittedAt(Instant.now());
        dischargeCase.setInsuranceClaim(claim);
        return saveAndRefreshStatus(dischargeCase);
    }

    public DischargeCase recordInsuranceDecision(String id, InsuranceDecisionRequest request, String reviewer) {
        DischargeCase dischargeCase = getCase(id);
        ensureNotDischarged(dischargeCase);
        InsuranceClaim claim = dischargeCase.getInsuranceClaim();
        if (claim == null || !"SUBMITTED".equals(claim.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no submitted claim awaiting decision");
        }
        if (!CLAIM_DECISIONS.contains(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED, PARTIALLY_APPROVED, or DENIED");
        }
        if (request.getApprovedAmount().compareTo(claim.getRequestedAmount()) > 0
                || request.getApprovedAmount().compareTo(dischargeCase.getInvoiceTotal()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approved amount cannot exceed the submitted claim");
        }
        if ("APPROVED".equals(request.getStatus())
                && request.getApprovedAmount().compareTo(claim.getRequestedAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A fully approved claim must cover the requested amount");
        }
        if ("DENIED".equals(request.getStatus()) && request.getApprovedAmount().compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A denied claim cannot have an approved amount");
        }
        claim.setStatus(request.getStatus());
        claim.setApprovedAmount(request.getApprovedAmount());
        claim.setClaimReference(request.getClaimReference());
        claim.setDecisionNotes(request.getDecisionNotes());
        claim.setDecidedAt(Instant.now());
        dischargeCase.setClaimUpdatedBy(reviewer);
        return saveAndRefreshStatus(dischargeCase);
    }

    public DischargeCase recordPayment(String id, DischargePaymentRequest request) {
        DischargeCase dischargeCase = getCase(id);
        ensureNotDischarged(dischargeCase);
        if (!List.of("CASH", "ONLINE").contains(request.getMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method must be CASH or ONLINE");
        }
        BigDecimal pendingOnline = dischargeCase.getPayments().stream()
                .filter(payment -> "PENDING_PROVIDER".equals(payment.getStatus()))
                .map(DischargePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = dischargeCase.getBalanceDue().subtract(pendingOnline).max(BigDecimal.ZERO);
        if (request.getAmount().compareTo(available) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment amount exceeds the outstanding balance");
        }
        DischargePayment payment = new DischargePayment();
        payment.setId(UUID.randomUUID().toString());
        payment.setMethod(request.getMethod());
        payment.setAmount(request.getAmount());
        payment.setTransactionReference(request.getTransactionReference());
        payment.setCreatedAt(Instant.now());
        if ("CASH".equals(request.getMethod())) {
            payment.setStatus("RECEIVED");
            payment.setConfirmedAt(Instant.now());
        } else {
            payment.setStatus("PENDING_PROVIDER");
        }
        dischargeCase.getPayments().add(payment);
        return saveAndRefreshStatus(dischargeCase);
    }

    public DischargeCase completeDischarge(String id) {
        DischargeCase dischargeCase = getCase(id);
        if (!dischargeCase.isClearanceReady()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Clinical approval, insurance decision, and payment clearance are required");
        }
        List<Patient> patients = patientRepository.findAllByPatientId(dischargeCase.getPatientId());
        if (patients.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient record is missing or duplicated; discharge was not completed");
        }
        String date = LocalDate.now().toString();
        Patient patient = patients.get(0);
        nursingService.dischargePatient(patient);
        patient.setPatientDischargedate(date);
        patientRepository.save(patient);
        dischargeCase.setStatus("DISCHARGED");
        dischargeCase.setDischargeDate(date);
        dischargeCase.setUpdatedAt(Instant.now());
        return dischargeRepository.save(dischargeCase);
    }

    private DischargeCase getCase(String id) {
        return dischargeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discharge case not found"));
    }

    private void ensureNotDischarged(DischargeCase dischargeCase) {
        if ("DISCHARGED".equals(dischargeCase.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discharge is already complete");
        }
    }

    private DischargeCase saveAndRefreshStatus(DischargeCase dischargeCase) {
        dischargeCase.setStatus(dischargeCase.isClearanceReady() ? "READY_FOR_DISCHARGE" : "IN_PROGRESS");
        dischargeCase.setUpdatedAt(Instant.now());
        return dischargeRepository.save(dischargeCase);
    }
}
