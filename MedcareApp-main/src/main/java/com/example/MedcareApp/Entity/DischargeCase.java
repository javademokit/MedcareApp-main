package com.example.MedcareApp.Entity;

import com.example.MedcareApp.Entity.billing.DischargePayment;
import com.example.MedcareApp.Entity.billing.InsuranceClaim;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Document(collection = "discharge_cases")
public class DischargeCase {
    @Id
    private String id;
    private String patientId;
    private String patientName;
    private String admissionDate;
    private String diagnosis;
    private String dischargeSummary;
    private String attendingDoctor;
    private String clinicalStatus = "PENDING";
    private String clinicalApprovedBy;
    private Instant clinicalApprovedAt;
    private String claimUpdatedBy;
    private String status = "IN_PROGRESS";
    private BigDecimal invoiceTotal = BigDecimal.ZERO;
    private InsuranceClaim insuranceClaim;
    private List<DischargePayment> payments = new ArrayList<>();
    private String dischargeDate;
    private Instant createdAt;
    private Instant updatedAt;

    @Transient
    public BigDecimal getInsuranceCoveredAmount() {
        if (insuranceClaim == null || !"APPROVED".equals(insuranceClaim.getStatus())
                && !"PARTIALLY_APPROVED".equals(insuranceClaim.getStatus())) return BigDecimal.ZERO;
        return insuranceClaim.getApprovedAmount() == null ? BigDecimal.ZERO : insuranceClaim.getApprovedAmount();
    }

    @Transient
    public BigDecimal getPatientPaidAmount() {
        return payments.stream()
                .filter(payment -> "RECEIVED".equals(payment.getStatus()))
                .map(DischargePayment::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transient
    public BigDecimal getBalanceDue() {
        return invoiceTotal.subtract(getInsuranceCoveredAmount()).subtract(getPatientPaidAmount()).max(BigDecimal.ZERO);
    }

    @Transient
    public boolean isClearanceReady() {
        boolean claimResolved = insuranceClaim == null || "APPROVED".equals(insuranceClaim.getStatus())
                || "PARTIALLY_APPROVED".equals(insuranceClaim.getStatus())
                || "DENIED".equals(insuranceClaim.getStatus());
        return "APPROVED".equals(clinicalStatus) && claimResolved && getBalanceDue().compareTo(BigDecimal.ZERO) == 0;
    }
}
