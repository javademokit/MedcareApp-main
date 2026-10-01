package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.pharmacy.MedicationItem;
import com.example.MedcareApp.Entity.pharmacy.PrescriptionIssue;
import com.example.MedcareApp.Entity.pharmacy.PurchaseOrder;
import com.example.MedcareApp.Interafce.MedicationRepository;
import com.example.MedcareApp.Interafce.PrescriptionIssueRepository;
import com.example.MedcareApp.Interafce.PurchaseOrderRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PharmacyService {
    private final MedicationRepository medicationRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PrescriptionIssueRepository prescriptionIssueRepository;

    public List<MedicationItem> getInventory() {
        return medicationRepository.findAll();
    }

    public MedicationItem saveMedication(MedicationItem medication) {
        Instant now = Instant.now();
        if (medication.getId() == null) medication.setCreatedAt(now);
        medication.setUpdatedAt(now);
        return medicationRepository.save(medication);
    }

    public PurchaseOrder createPurchaseOrder(PurchaseOrder order) {
        MedicationItem medication = medicationRepository.findById(order.getMedicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        order.setMedicationName(medication.getName());
        order.setSupplier(order.getSupplier() == null || order.getSupplier().isBlank()
                ? medication.getSupplier() : order.getSupplier());
        order.setStatus("ORDERED");
        order.setCreatedAt(Instant.now());
        return purchaseOrderRepository.save(order);
    }

    public List<PurchaseOrder> getPurchaseOrders() {
        return purchaseOrderRepository.findAll();
    }

    public PurchaseOrder receivePurchaseOrder(String id) {
        PurchaseOrder order = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order not found"));
        if ("RECEIVED".equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Purchase order has already been received");
        }
        MedicationItem medication = medicationRepository.findById(order.getMedicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        medication.setQuantityOnHand(medication.getQuantityOnHand() + order.getQuantity());
        medication.setUpdatedAt(Instant.now());
        medicationRepository.save(medication);
        order.setStatus("RECEIVED");
        order.setReceivedAt(Instant.now());
        return purchaseOrderRepository.save(order);
    }

    public PrescriptionIssue createPrescriptionIssue(PrescriptionIssue issue) {
        MedicationItem medication = medicationRepository.findById(issue.getMedicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        issue.setMedicationName(medication.getName());
        issue.setStatus("PENDING");
        issue.setCreatedAt(Instant.now());
        return prescriptionIssueRepository.save(issue);
    }

    public List<PrescriptionIssue> getPrescriptionIssues() {
        return prescriptionIssueRepository.findAll();
    }

    public PrescriptionIssue dispensePrescription(String id, String issuedBy) {
        PrescriptionIssue issue = prescriptionIssueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription issue not found"));
        if (!"PENDING".equals(issue.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prescription is not pending");
        }
        MedicationItem medication = medicationRepository.findById(issue.getMedicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        if (medication.getQuantityOnHand() < issue.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock to dispense this prescription");
        }
        medication.setQuantityOnHand(medication.getQuantityOnHand() - issue.getQuantity());
        medication.setUpdatedAt(Instant.now());
        medicationRepository.save(medication);
        issue.setStatus("ISSUED");
        issue.setIssuedBy(issuedBy);
        issue.setIssuedAt(Instant.now());
        return prescriptionIssueRepository.save(issue);
    }
}
