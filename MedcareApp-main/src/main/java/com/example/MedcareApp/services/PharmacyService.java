package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.pharmacy.MedicationItem;
import com.example.MedcareApp.Entity.pharmacy.MedicationPrescription;
import com.example.MedcareApp.Entity.pharmacy.PrescriptionIssue;
import com.example.MedcareApp.Entity.pharmacy.PharmacyInvoice;
import com.example.MedcareApp.Entity.pharmacy.PurchaseOrder;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Interafce.MedicationPrescriptionRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.Interafce.MedicationRepository;
import com.example.MedcareApp.Interafce.PrescriptionIssueRepository;
import com.example.MedcareApp.Interafce.PurchaseOrderRepository;
import com.example.MedcareApp.web.MedicationPrescriptionRequest;
import com.example.MedcareApp.web.PrescribableMedication;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
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
    private final PatientRepository patientRepository;
    private final MedicationPrescriptionRepository medicationPrescriptionRepository;
    private final PharmacyBillingService pharmacyBillingService;

    public List<MedicationItem> getInventory() {
        return medicationRepository.findAll();
    }

    public MedicationItem saveMedication(MedicationItem medication) {
        if (medication.getExpiryDate() != null && !medication.getExpiryDate().isBlank()) {
            parseExpiryDate(medication.getExpiryDate());
        }
        if (medication.getDepartment() == null || medication.getDepartment().isBlank()) {
            medication.setDepartment("General Medicine");
        } else {
            medication.setDepartment(medication.getDepartment().trim());
        }
        Instant now = Instant.now();
        if (medication.getId() == null) medication.setCreatedAt(now);
        medication.setUpdatedAt(now);
        return medicationRepository.save(medication);
    }

    public List<PrescribableMedication> getPrescribableMedications() {
        LocalDate today = LocalDate.now();
        return medicationRepository.findAll().stream()
                .filter(item -> item.getQuantityOnHand() > 0)
                .filter(item -> item.getExpiryDate() == null || item.getExpiryDate().isBlank()
                        || !parseExpiryDate(item.getExpiryDate()).isBefore(today))
                .sorted(Comparator.comparing(MedicationItem::getDepartment,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(MedicationItem::getName, String.CASE_INSENSITIVE_ORDER))
                .map(item -> new PrescribableMedication(item.getId(), item.getName(),
                        item.getDepartment() == null ? "General Medicine" : item.getDepartment(),
                        item.getStrength(), item.getDosageForm(), item.getQuantityOnHand()))
                .toList();
    }

    public MedicationPrescription createMedicationPrescription(
            String consultationId,
            String appointmentId,
            Patient patient,
            String doctorId,
            String doctorName,
            String diagnosis,
            List<MedicationPrescriptionRequest.MedicationOrder> orders) {
        if (orders == null || orders.isEmpty()) return null;
        MedicationPrescription prescription = new MedicationPrescription();
        prescription.setConsultationId(consultationId);
        prescription.setAppointmentId(appointmentId);
        prescription.setPatientId(patient.getPatientId());
        prescription.setPatientName(patient.getPatientName());
        prescription.setDoctorId(doctorId);
        prescription.setDoctorName(doctorName);
        prescription.setDiagnosis(diagnosis);
        List<MedicationPrescription.MedicationLine> lines = new ArrayList<>();
        Set<String> selectedMedicationIds = new HashSet<>();
        for (MedicationPrescriptionRequest.MedicationOrder order : orders) {
            if (order == null || order.getQuantity() < 1 || blank(order.getMedicationId())
                    || blank(order.getDose()) || blank(order.getRoute())
                    || blank(order.getFrequency()) || blank(order.getDuration())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each prescribed medicine requires a catalog item, dose, route, frequency, duration, and quantity");
            }
            if (!selectedMedicationIds.add(order.getMedicationId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Select each medicine only once per prescription");
            }
            MedicationItem item = medicationRepository.findById(order.getMedicationId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "A selected medicine is no longer in the pharmacy catalog"));
            if (item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        item.getName() + " has no valid price in the pharmacy catalog");
            }
            MedicationPrescription.MedicationLine line = new MedicationPrescription.MedicationLine();
            line.setMedicationId(item.getId());
            line.setName(item.getName());
            line.setDepartment(item.getDepartment() == null ? "General Medicine" : item.getDepartment());
            line.setStrength(item.getStrength());
            line.setDosageForm(item.getDosageForm());
            line.setDose(order.getDose().trim());
            line.setRoute(order.getRoute().trim());
            line.setFrequency(order.getFrequency().trim());
            line.setDuration(order.getDuration().trim());
            line.setQuantity(order.getQuantity());
            line.setUnitPrice(item.getUnitPrice());
            line.setInstructions(blank(order.getInstructions()) ? null : order.getInstructions().trim());
            lines.add(line);
        }
        prescription.setMedications(lines);
        MedicationPrescription savedPrescription = medicationPrescriptionRepository.save(prescription);
        pharmacyBillingService.createInvoiceForPrescription(savedPrescription);
        return savedPrescription;
    }

    public List<MedicationPrescription> getMedicationPrescriptions() {
        List<MedicationPrescription> prescriptions = medicationPrescriptionRepository.findAllByOrderByCreatedAtDesc();
        prescriptions.stream().filter(prescription -> "PENDING".equals(prescription.getStatus()))
                .forEach(pharmacyBillingService::createInvoiceForPrescription);
        return prescriptions;
    }

    public List<MedicationPrescription> getMedicationPrescriptionsForPatient(String patientId) {
        return medicationPrescriptionRepository.findAllByPatientIdOrderByCreatedAtDesc(patientId);
    }

    public List<PharmacyInvoice> getPharmacyInvoices() {
        return pharmacyBillingService.getInvoices();
    }

    public MedicationPrescription dispenseMedicationPrescription(String id, String issuedBy) {
        MedicationPrescription prescription = medicationPrescriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription not found"));
        if (!"PENDING".equals(prescription.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prescription has already been dispensed");
        }
        pharmacyBillingService.requirePaidPrescription(prescription);
        LocalDate today = LocalDate.now();
        List<MedicationItem> stockItems = new ArrayList<>();
        for (MedicationPrescription.MedicationLine line : prescription.getMedications()) {
            MedicationItem medication = medicationRepository.findById(line.getMedicationId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "A prescribed medicine is no longer in the pharmacy catalog"));
            if (medication.getExpiryDate() != null && !medication.getExpiryDate().isBlank()
                    && parseExpiryDate(medication.getExpiryDate()).isBefore(today)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        medication.getName() + " is expired and cannot be dispensed");
            }
            if (medication.getQuantityOnHand() < line.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient stock for " + medication.getName() + "; prescription was not dispensed");
            }
            stockItems.add(medication);
        }
        for (int index = 0; index < prescription.getMedications().size(); index++) {
            MedicationPrescription.MedicationLine line = prescription.getMedications().get(index);
            MedicationItem medication = stockItems.get(index);
            medication.setQuantityOnHand(medication.getQuantityOnHand() - line.getQuantity());
            medication.setUpdatedAt(Instant.now());
            medicationRepository.save(medication);
            line.setStatus("DISPENSED");
        }
        prescription.setStatus("DISPENSED");
        prescription.setDispensedBy(issuedBy);
        prescription.setDispensedAt(Instant.now());
        return medicationPrescriptionRepository.save(prescription);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private LocalDate parseExpiryDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date must use YYYY-MM-DD format");
        }
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
        var patients = patientRepository.findAllByPatientId(issue.getPatientId());
        if (patients.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient ID not found");
        }
        if (patients.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient ID is not unique");
        }
        MedicationItem medication = medicationRepository.findById(issue.getMedicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        if (medication.getUnitPrice() == null || medication.getUnitPrice().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    medication.getName() + " has no valid price in the pharmacy catalog");
        }
        issue.setPatientName(patients.get(0).getPatientName());
        issue.setMedicationName(medication.getName());
        issue.setStatus("PENDING");
        issue.setCreatedAt(Instant.now());
        PrescriptionIssue savedIssue = prescriptionIssueRepository.save(issue);
        pharmacyBillingService.createInvoiceForIssue(savedIssue);
        return savedIssue;
    }

    public List<PrescriptionIssue> getPrescriptionIssues() {
        List<PrescriptionIssue> issues = prescriptionIssueRepository.findAll();
        issues.stream().filter(issue -> "PENDING".equals(issue.getStatus()))
                .forEach(pharmacyBillingService::createInvoiceForIssue);
        return issues;
    }

    public PrescriptionIssue dispensePrescription(String id, String issuedBy) {
        PrescriptionIssue issue = prescriptionIssueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription issue not found"));
        if (!"PENDING".equals(issue.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prescription is not pending");
        }
        pharmacyBillingService.requirePaidIssue(issue);
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
