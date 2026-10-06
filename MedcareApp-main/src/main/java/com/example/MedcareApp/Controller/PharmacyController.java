package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.pharmacy.MedicationItem;
import com.example.MedcareApp.Entity.pharmacy.MedicationPrescription;
import com.example.MedcareApp.Entity.pharmacy.PrescriptionIssue;
import com.example.MedcareApp.Entity.pharmacy.PurchaseOrder;
import com.example.MedcareApp.Entity.pharmacy.PharmacyInvoice;
import com.example.MedcareApp.services.PharmacyBillingService;
import com.example.MedcareApp.services.PharmacyService;
import com.example.MedcareApp.web.AppointmentPaymentRequest;
import com.example.MedcareApp.web.PaymentCheckoutRequest;
import com.example.MedcareApp.web.PaymentVerificationRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/pharmacy")
@RequiredArgsConstructor
public class PharmacyController {
    private final PharmacyService pharmacyService;
    private final PharmacyBillingService pharmacyBillingService;

    @GetMapping("/medications")
    public List<MedicationItem> getInventory() {
        return pharmacyService.getInventory();
    }

    @GetMapping("/prescriptions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','CRM_EXECUTIVE','PHARMACIST')")
    public List<MedicationPrescription> getMedicationPrescriptions() {
        return pharmacyService.getMedicationPrescriptions();
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','CRM_EXECUTIVE','PHARMACIST','BILLING_EXECUTIVE','FINANCE')")
    public List<PharmacyInvoice> getPharmacyInvoices() {
        return pharmacyService.getPharmacyInvoices();
    }

    @GetMapping("/invoices/gateways")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','CRM_EXECUTIVE','PHARMACIST','BILLING_EXECUTIVE','FINANCE')")
    public Map<String, Object> getPaymentGateways() {
        return pharmacyBillingService.configuredGateways();
    }

    @PostMapping("/invoices/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','CRM_EXECUTIVE','PHARMACIST','BILLING_EXECUTIVE','FINANCE')")
    public PharmacyInvoice recordPayment(
            @PathVariable String id, @Valid @RequestBody AppointmentPaymentRequest request, Principal principal) {
        return pharmacyBillingService.recordPayment(id, request, principal.getName());
    }

    @PostMapping("/invoices/{id}/checkout")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','CRM_EXECUTIVE','PHARMACIST','BILLING_EXECUTIVE','FINANCE')")
    public Map<String, Object> createCheckout(
            @PathVariable String id, @Valid @RequestBody PaymentCheckoutRequest request) {
        return pharmacyBillingService.createCheckout(id, request.getProvider());
    }

    @PostMapping("/invoices/{id}/verify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','CRM_EXECUTIVE','PHARMACIST','BILLING_EXECUTIVE','FINANCE')")
    public PharmacyInvoice verifyCheckout(
            @PathVariable String id, @Valid @RequestBody PaymentVerificationRequest request) {
        return pharmacyBillingService.verifyCheckout(id, request);
    }

    @PostMapping("/prescriptions/{id}/dispense")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','CRM_EXECUTIVE','PHARMACIST')")
    public MedicationPrescription dispenseMedicationPrescription(
            @PathVariable String id, Principal principal) {
        return pharmacyService.dispenseMedicationPrescription(id, principal.getName());
    }

    @PostMapping("/medications")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicationItem createMedication(@Valid @RequestBody MedicationItem medication) {
        return pharmacyService.saveMedication(medication);
    }

    @PutMapping("/medications/{id}")
    public MedicationItem updateMedication(@PathVariable String id, @Valid @RequestBody MedicationItem medication) {
        medication.setId(id);
        return pharmacyService.saveMedication(medication);
    }

    @GetMapping("/purchase-orders")
    public List<PurchaseOrder> getPurchaseOrders() {
        return pharmacyService.getPurchaseOrders();
    }

    @PostMapping("/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrder createPurchaseOrder(@Valid @RequestBody PurchaseOrder order) {
        return pharmacyService.createPurchaseOrder(order);
    }

    @PostMapping("/purchase-orders/{id}/receive")
    public PurchaseOrder receivePurchaseOrder(@PathVariable String id) {
        return pharmacyService.receivePurchaseOrder(id);
    }

    @GetMapping("/prescription-issues")
    public List<PrescriptionIssue> getPrescriptionIssues() {
        return pharmacyService.getPrescriptionIssues();
    }

    @PostMapping("/prescription-issues")
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionIssue createPrescriptionIssue(@Valid @RequestBody PrescriptionIssue issue) {
        return pharmacyService.createPrescriptionIssue(issue);
    }

    @PostMapping("/prescription-issues/{id}/dispense")
    public PrescriptionIssue dispensePrescription(@PathVariable String id, Principal principal) {
        return pharmacyService.dispensePrescription(id, principal.getName());
    }
}
