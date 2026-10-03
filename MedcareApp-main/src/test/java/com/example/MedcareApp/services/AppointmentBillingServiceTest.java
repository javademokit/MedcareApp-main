package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.billing.AppointmentInvoice;
import com.example.MedcareApp.Interafce.AppointmentInvoiceRepository;
import com.example.MedcareApp.web.AppointmentPaymentRequest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AppointmentBillingServiceTest {
    @Mock private AppointmentInvoiceRepository invoiceRepository;
    @InjectMocks private AppointmentBillingService billingService;

    @Test
    void paymentIsRecordedAndInvoiceBecomesPaid() {
        AppointmentInvoice invoice = invoice("500.00", "0.00");
        when(invoiceRepository.findById("invoice-1")).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(AppointmentInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AppointmentPaymentRequest request = payment("500", "CASH");

        AppointmentInvoice updated = billingService.recordPayment("invoice-1", request, "cashier@example.test");

        assertEquals("PAID", updated.getStatus());
        assertEquals(new BigDecimal("500.00"), updated.getPaidAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), updated.getBalanceDue());
        assertEquals("cashier@example.test", updated.getPayments().get(0).getReceivedBy());
    }

    @Test
    void overpaymentIsRejectedWithoutSavingInvoice() {
        AppointmentInvoice invoice = invoice("500.00", "0.00");
        when(invoiceRepository.findById("invoice-1")).thenReturn(Optional.of(invoice));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> billingService.recordPayment("invoice-1", payment("501", "UPI"), "cashier"));

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void appointmentInvoiceIsGeneratedFromPersistedAppointmentFee() {
        Appointment appointment = new Appointment();
        appointment.setId("appointment-1");
        appointment.setPatientId("PT-1");
        appointment.setPatientName("Test patient");
        appointment.setDoctor("Dr. Example");
        appointment.setFee("1250");
        when(invoiceRepository.findByAppointmentId("appointment-1")).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(AppointmentInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentInvoice invoice = billingService.createInvoice(appointment);

        assertEquals("appointment-1", invoice.getAppointmentId());
        assertEquals(new BigDecimal("1250.00"), invoice.getAmount());
        assertEquals("PENDING", invoice.getStatus());
    }

    private AppointmentInvoice invoice(String amount, String paidAmount) {
        AppointmentInvoice invoice = new AppointmentInvoice();
        invoice.setId("invoice-1");
        invoice.setAmount(new BigDecimal(amount));
        invoice.setPaidAmount(new BigDecimal(paidAmount));
        invoice.setStatus("PENDING");
        return invoice;
    }

    private AppointmentPaymentRequest payment(String amount, String method) {
        AppointmentPaymentRequest request = new AppointmentPaymentRequest();
        request.setAmount(new BigDecimal(amount));
        request.setMethod(method);
        return request;
    }
}
