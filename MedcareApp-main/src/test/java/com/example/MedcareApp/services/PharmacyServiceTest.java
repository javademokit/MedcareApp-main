package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.pharmacy.MedicationItem;
import com.example.MedcareApp.Entity.pharmacy.MedicationPrescription;
import com.example.MedcareApp.Entity.pharmacy.PrescriptionIssue;
import com.example.MedcareApp.Entity.pharmacy.PurchaseOrder;
import com.example.MedcareApp.Interafce.MedicationRepository;
import com.example.MedcareApp.Interafce.MedicationPrescriptionRepository;
import com.example.MedcareApp.Interafce.PrescriptionIssueRepository;
import com.example.MedcareApp.Interafce.PurchaseOrderRepository;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.web.MedicationPrescriptionRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PharmacyServiceTest {
    @Mock private MedicationRepository medicationRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PrescriptionIssueRepository prescriptionIssueRepository;
    @Mock private MedicationPrescriptionRepository medicationPrescriptionRepository;
    @InjectMocks private PharmacyService pharmacyService;

    @Test
    void receivingOrderAddsQuantityToStock() {
        MedicationItem medication = new MedicationItem();
        medication.setId("med-1");
        medication.setName("Demo medicine");
        medication.setQuantityOnHand(4);
        PurchaseOrder order = new PurchaseOrder();
        order.setId("po-1");
        order.setMedicationId("med-1");
        order.setQuantity(6);
        order.setStatus("ORDERED");
        when(purchaseOrderRepository.findById("po-1")).thenReturn(Optional.of(order));
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(medication));
        when(medicationRepository.save(any(MedicationItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder received = pharmacyService.receivePurchaseOrder("po-1");

        assertEquals(10, medication.getQuantityOnHand());
        assertEquals("RECEIVED", received.getStatus());
        verify(medicationRepository).save(medication);
    }

    @Test
    void dispensingCannotMakeInventoryNegative() {
        MedicationItem medication = new MedicationItem();
        medication.setId("med-1");
        medication.setQuantityOnHand(2);
        PrescriptionIssue issue = new PrescriptionIssue();
        issue.setId("issue-1");
        issue.setMedicationId("med-1");
        issue.setQuantity(3);
        issue.setStatus("PENDING");
        when(prescriptionIssueRepository.findById("issue-1")).thenReturn(Optional.of(issue));
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(medication));

        assertThrows(ResponseStatusException.class, () -> pharmacyService.dispensePrescription("issue-1", "staff@example.org"));
        verify(medicationRepository, never()).save(any(MedicationItem.class));
    }

    @Test
    void doctorPrescriptionCanBeCreatedAndDispensedAsOneOrder() {
        MedicationItem medication = new MedicationItem();
        medication.setId("med-1");
        medication.setName("Demo medicine");
        medication.setDepartment("Pediatrics");
        medication.setQuantityOnHand(12);
        MedicationPrescriptionRequest.MedicationOrder order = new MedicationPrescriptionRequest.MedicationOrder();
        order.setMedicationId("med-1");
        order.setDose("1 tablet");
        order.setRoute("Oral");
        order.setFrequency("Twice daily");
        order.setDuration("5 days");
        order.setQuantity(10);
        Patient patient = new Patient();
        patient.setPatientId("PT-1");
        patient.setPatientName("Demo patient");
        when(medicationRepository.findById("med-1")).thenReturn(Optional.of(medication));
        when(medicationPrescriptionRepository.save(any(MedicationPrescription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MedicationPrescription prescription = pharmacyService.createMedicationPrescription(
                "consultation-1", "appointment-1", patient, "doctor-1", "Dr. Example",
                "Diagnosis", List.of(order));

        assertEquals("Pediatrics", prescription.getMedications().get(0).getDepartment());
        prescription.setId("rx-1");
        when(medicationPrescriptionRepository.findById("rx-1")).thenReturn(Optional.of(prescription));
        when(medicationRepository.save(any(MedicationItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicationPrescription dispensed = pharmacyService.dispenseMedicationPrescription("rx-1", "pharmacist");

        assertEquals(2, medication.getQuantityOnHand());
        assertEquals("DISPENSED", dispensed.getStatus());
        assertEquals("DISPENSED", dispensed.getMedications().get(0).getStatus());
    }
}
