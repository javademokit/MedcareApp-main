package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.pharmacy.MedicationItem;
import com.example.MedcareApp.Entity.pharmacy.PrescriptionIssue;
import com.example.MedcareApp.Entity.pharmacy.PurchaseOrder;
import com.example.MedcareApp.services.PharmacyService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
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

@RestController
@RequestMapping("/api/pharmacy")
@RequiredArgsConstructor
public class PharmacyController {
    private final PharmacyService pharmacyService;

    @GetMapping("/medications")
    public List<MedicationItem> getInventory() {
        return pharmacyService.getInventory();
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
