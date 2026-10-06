package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.pharmacy.PharmacyInvoice;
import com.example.MedcareApp.Interafce.MedicationRepository;
import com.example.MedcareApp.Interafce.PharmacyInvoiceRepository;
import com.example.MedcareApp.web.AppointmentPaymentRequest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class PharmacyBillingServiceTest {
    @Mock private PharmacyInvoiceRepository invoiceRepository;
    @Mock private MedicationRepository medicationRepository;
    @Mock private RestClient.Builder restClientBuilder;
    @InjectMocks private PharmacyBillingService billingService;

    @Test
    void collectingCashUpdatesPaymentAndOutstandingBalance() {
        PharmacyInvoice invoice = new PharmacyInvoice();
        invoice.setId("invoice-1");
        invoice.setAmount(new BigDecimal("250.00"));
        invoice.setPaidAmount(new BigDecimal("50.00"));
        invoice.setStatus("PARTIALLY_PAID");
        when(invoiceRepository.findById("invoice-1")).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(PharmacyInvoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentPaymentRequest request = new AppointmentPaymentRequest();
        request.setAmount(new BigDecimal("200.00"));
        request.setMethod("cash");
        request.setReference("cash-desk-1");

        PharmacyInvoice paid = billingService.recordPayment("invoice-1", request, "pharmacist@example.org");

        assertEquals(new BigDecimal("250.00"), paid.getPaidAmount());
        assertEquals(new BigDecimal("0.00"), paid.getBalanceDue());
        assertEquals("PAID", paid.getStatus());
        assertEquals("CASH", paid.getPayments().get(0).getMethod());
        assertEquals("cash-desk-1", paid.getPayments().get(0).getReference());
        assertEquals("pharmacist@example.org", paid.getPayments().get(0).getReceivedBy());
    }
}
