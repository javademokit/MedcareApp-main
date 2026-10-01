package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.DischargeCase;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.billing.DischargePayment;
import com.example.MedcareApp.Interafce.DischargeCaseRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DischargeServiceTest {
    @Mock private DischargeCaseRepository dischargeRepository;
    @Mock private PatientRepository patientRepository;
    @InjectMocks private DischargeService service;

    @Test
    void pendingOnlinePaymentDoesNotClearBalance() {
        DischargeCase dischargeCase = new DischargeCase();
        dischargeCase.setClinicalStatus("APPROVED");
        dischargeCase.setInvoiceTotal(new BigDecimal("100.00"));
        DischargePayment payment = new DischargePayment();
        payment.setAmount(new BigDecimal("100.00"));
        payment.setStatus("PENDING_PROVIDER");
        dischargeCase.setPayments(List.of(payment));

        assertFalse(dischargeCase.isClearanceReady());
        assertEquals(new BigDecimal("100.00"), dischargeCase.getBalanceDue());
    }

    @Test
    void dischargeCannotCompleteBeforeClinicalApproval() {
        DischargeCase dischargeCase = new DischargeCase();
        dischargeCase.setId("case-1");
        dischargeCase.setInvoiceTotal(BigDecimal.ZERO);
        when(dischargeRepository.findById("case-1")).thenReturn(Optional.of(dischargeCase));

        assertThrows(ResponseStatusException.class, () -> service.completeDischarge("case-1"));
    }

    @Test
    void fullyClearedDischargeUpdatesPatientRecord() {
        DischargeCase dischargeCase = new DischargeCase();
        dischargeCase.setId("case-2");
        dischargeCase.setPatientId("P-42");
        dischargeCase.setClinicalStatus("APPROVED");
        dischargeCase.setInvoiceTotal(new BigDecimal("80.00"));
        DischargePayment cashPayment = new DischargePayment();
        cashPayment.setAmount(new BigDecimal("80.00"));
        cashPayment.setStatus("RECEIVED");
        dischargeCase.setPayments(List.of(cashPayment));
        Patient patient = new Patient();
        patient.setPatientId("P-42");
        when(dischargeRepository.findById("case-2")).thenReturn(Optional.of(dischargeCase));
        when(patientRepository.findAllByPatientId("P-42")).thenReturn(List.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dischargeRepository.save(any(DischargeCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DischargeCase completed = service.completeDischarge("case-2");

        assertEquals("DISCHARGED", completed.getStatus());
        assertEquals(completed.getDischargeDate(), patient.getPatientDischargedate());
        assertTrue(completed.isClearanceReady());
        verify(patientRepository).save(patient);
    }
}
