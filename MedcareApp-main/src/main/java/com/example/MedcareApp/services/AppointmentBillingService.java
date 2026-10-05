package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.billing.AppointmentInvoice;
import com.example.MedcareApp.Interafce.AppointmentInvoiceRepository;
import com.example.MedcareApp.web.AppointmentPaymentRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AppointmentBillingService {
    private static final List<String> RECEIVED_METHODS = List.of("CASH", "CARD", "UPI", "BANK_TRANSFER");
    private final AppointmentInvoiceRepository invoiceRepository;

    public AppointmentInvoice createInvoice(Appointment appointment) {
        return invoiceRepository.findByAppointmentId(appointment.getId())
                .orElseGet(() -> saveNewInvoice(appointment));
    }

    private AppointmentInvoice saveNewInvoice(Appointment appointment) {
        AppointmentInvoice invoice = new AppointmentInvoice();
        invoice.setAppointmentId(appointment.getId());
        invoice.setPatientId(appointment.getPatientId());
        invoice.setPatientName(appointment.getPatientName());
        invoice.setPatientEmail(appointment.getPatientEmailId());
        invoice.setPatientMobile(appointment.getMobileNo());
        invoice.setDoctorName(appointment.getDoctor());
        BigDecimal amount;
        try {
            amount = new BigDecimal(appointment.getFee());
        } catch (NumberFormatException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment fee is invalid");
        }
        if (amount.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment fee cannot be negative");
        }
        invoice.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        invoice.setStatus(amount.signum() == 0 ? "NO_CHARGE" : "PENDING");
        return saveInvoice(invoice);
    }

    public List<AppointmentInvoice> getInvoices() {
        return invoiceRepository.findAllByOrderByCreatedAtDesc();
    }

    public AppointmentInvoice getInvoiceForAppointment(String appointmentId) {
        return invoiceRepository.findByAppointmentId(appointmentId).orElse(null);
    }

    public List<AppointmentInvoice> getInvoicesForAppointments(List<String> appointmentIds) {
        return appointmentIds.isEmpty() ? List.of() : invoiceRepository.findAllByAppointmentIdIn(appointmentIds);
    }

    public AppointmentInvoice recordPayment(String invoiceId, AppointmentPaymentRequest request, String receivedBy) {
        AppointmentInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment invoice not found"));
        java.time.Instant expiration = Instant.now().minus(java.time.Duration.ofMinutes(30));
        for (AppointmentInvoice.Payment payment : invoice.getPayments()) {
            if ("PENDING".equals(payment.getStatus()) && payment.getCreatedAt() != null
                    && payment.getCreatedAt().isBefore(expiration)) {
                payment.setStatus("EXPIRED");
            }
        }
        String method = request.getMethod().trim().toUpperCase(Locale.ROOT);
        if (!RECEIVED_METHODS.contains(method)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment method must be CASH, CARD, UPI, or BANK_TRANSFER");
        }
        if (List.of("VOID", "REFUND_REQUIRED").contains(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled appointment invoices cannot accept payment");
        }
        if (invoice.getPayments().stream().anyMatch(payment -> "PENDING".equals(payment.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A gateway payment is pending. Wait for confirmation or retry after it expires.");
        }
        if (invoice.getBalanceDue().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice has no outstanding balance");
        }
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.compareTo(invoice.getBalanceDue()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment amount must be positive and cannot exceed the outstanding balance");
        }
        AppointmentInvoice.Payment payment = new AppointmentInvoice.Payment();
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setReference(StringUtils.hasText(request.getReference()) ? request.getReference().trim() : null);
        payment.setReceivedBy(receivedBy);
        invoice.getPayments().add(payment);
        invoice.setPaidAmount(invoice.getPaidAmount().add(amount).setScale(2, RoundingMode.HALF_UP));
        invoice.setStatus(invoice.getBalanceDue().signum() == 0 ? "PAID" : "PARTIALLY_PAID");
        return saveInvoice(invoice);
    }

    public void cancelInvoice(String appointmentId) {
        invoiceRepository.findByAppointmentId(appointmentId).ifPresent(invoice -> {
            invoice.setStatus(invoice.getPaidAmount().signum() == 0 ? "VOID" : "REFUND_REQUIRED");
            saveInvoice(invoice);
        });
    }

    private AppointmentInvoice saveInvoice(AppointmentInvoice invoice) {
        try {
            return invoiceRepository.save(invoice);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invoice changed during payment processing. Refresh and try again.", exception);
        }
    }
}
