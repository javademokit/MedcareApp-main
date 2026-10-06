package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.pharmacy.MedicationItem;
import com.example.MedcareApp.Entity.pharmacy.MedicationPrescription;
import com.example.MedcareApp.Entity.pharmacy.PharmacyInvoice;
import com.example.MedcareApp.Entity.pharmacy.PrescriptionIssue;
import com.example.MedcareApp.Interafce.MedicationRepository;
import com.example.MedcareApp.Interafce.PharmacyInvoiceRepository;
import com.example.MedcareApp.web.AppointmentPaymentRequest;
import com.example.MedcareApp.web.PaymentVerificationRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PharmacyBillingService {
    private static final List<String> RECEIVED_METHODS = List.of("CASH", "CARD", "UPI", "BANK_TRANSFER");
    private final PharmacyInvoiceRepository invoiceRepository;
    private final MedicationRepository medicationRepository;
    private final RestClient.Builder restClientBuilder;

    @Value("${PAYMENTS_RAZORPAY_KEY_ID:}")
    private String razorpayKeyId;
    @Value("${PAYMENTS_RAZORPAY_KEY_SECRET:}")
    private String razorpaySecret;

    public PharmacyInvoice createInvoiceForPrescription(MedicationPrescription prescription) {
        String referenceKey = "PRESCRIPTION:" + prescription.getId();
        return invoiceRepository.findByReferenceKey(referenceKey)
                .orElseGet(() -> saveNewInvoice(referenceKey, "PRESCRIPTION", prescription.getId(),
                        prescription.getPatientId(), prescription.getPatientName(),
                        prescription.getMedications().stream().map(line -> {
                            MedicationItem medication = medicationRepository.findById(line.getMedicationId())
                                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                            "A prescribed medicine is no longer in the pharmacy catalog"));
                            BigDecimal price = line.getUnitPrice() == null
                                    ? medication.getUnitPrice() : line.getUnitPrice();
                            return invoiceLine(line.getMedicationId(), line.getName(), line.getStrength(),
                                    line.getDosageForm(), line.getQuantity(), price);
                        }).toList()));
    }

    public PharmacyInvoice createInvoiceForIssue(PrescriptionIssue issue) {
        String referenceKey = "ISSUE:" + issue.getId();
        return invoiceRepository.findByReferenceKey(referenceKey)
                .orElseGet(() -> {
                    MedicationItem medication = medicationRepository.findById(issue.getMedicationId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "Medication not found"));
                    return saveNewInvoice(referenceKey, "ISSUE", issue.getId(), issue.getPatientId(),
                            issue.getPatientName(), List.of(invoiceLine(medication.getId(),
                                    medication.getName(), medication.getStrength(), medication.getDosageForm(),
                                    issue.getQuantity(), medication.getUnitPrice())));
                });
    }

    public List<PharmacyInvoice> getInvoices() {
        return invoiceRepository.findAllByOrderByCreatedAtDesc();
    }

    public PharmacyInvoice recordPayment(
            String invoiceId, AppointmentPaymentRequest request, String receivedBy) {
        PharmacyInvoice invoice = findInvoice(invoiceId);
        expireStaleAttempts(invoice);
        String method = request.getMethod().trim().toUpperCase(Locale.ROOT);
        if (!RECEIVED_METHODS.contains(method)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment method must be CASH, CARD, UPI, or BANK_TRANSFER");
        }
        if (invoice.getPayments().stream().anyMatch(payment -> "PENDING".equals(payment.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An online payment is pending. Wait for confirmation or retry after it expires.");
        }
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.compareTo(invoice.getBalanceDue()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment amount must be positive and cannot exceed the outstanding balance");
        }
        PharmacyInvoice.Payment payment = new PharmacyInvoice.Payment();
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setReference(StringUtils.hasText(request.getReference()) ? request.getReference().trim() : null);
        payment.setReceivedBy(receivedBy);
        invoice.getPayments().add(payment);
        applyPayment(invoice, amount);
        return saveInvoice(invoice);
    }

    public Map<String, Object> configuredGateways() {
        return Map.of("RAZORPAY", StringUtils.hasText(razorpayKeyId) && StringUtils.hasText(razorpaySecret));
    }

    public Map<String, Object> createCheckout(String invoiceId, String requestedProvider) {
        if (!"RAZORPAY".equalsIgnoreCase(requestedProvider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported pharmacy payment provider");
        }
        requireRazorpayConfiguration();
        PharmacyInvoice invoice = findInvoice(invoiceId);
        if (invoice.getBalanceDue().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice has no payable balance");
        }
        expireStaleAttempts(invoice);
        if (invoice.getPayments().stream().anyMatch(payment -> "PENDING".equals(payment.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An online payment attempt is already pending for this invoice");
        }
        PharmacyInvoice.Payment attempt = new PharmacyInvoice.Payment();
        attempt.setAmount(invoice.getBalanceDue().setScale(2, RoundingMode.HALF_UP));
        attempt.setStatus("PENDING");
        attempt.setProvider("RAZORPAY");
        attempt.setMethod("RAZORPAY");
        try {
            RazorpayOrder response = restClientBuilder.build().post().uri("https://api.razorpay.com/v1/orders")
                    .headers(headers -> headers.setBasicAuth(razorpayKeyId, razorpaySecret))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", attempt.getAmount().movePointRight(2).intValueExact(),
                            "currency", invoice.getCurrency(), "receipt", invoice.getInvoiceNumber()))
                    .retrieve().body(RazorpayOrder.class);
            if (response == null || !StringUtils.hasText(response.id())) {
                throw new IllegalStateException("Razorpay did not return an order ID");
            }
            attempt.setGatewayOrderId(response.id());
            invoice.getPayments().add(attempt);
            saveInvoice(invoice);
            return Map.of("provider", "RAZORPAY", "keyId", razorpayKeyId, "orderId", response.id(),
                    "amount", attempt.getAmount().movePointRight(2).intValueExact(),
                    "currency", invoice.getCurrency(), "name", invoice.getPatientName(),
                    "description", "Pharmacy invoice " + invoice.getInvoiceNumber());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not create a Razorpay pharmacy payment request", exception);
        }
    }

    public PharmacyInvoice verifyCheckout(String invoiceId, PaymentVerificationRequest request) {
        requireRazorpayConfiguration();
        PharmacyInvoice invoice = findInvoice(invoiceId);
        PharmacyInvoice.Payment attempt = invoice.getPayments().stream()
                .filter(payment -> request.getGatewayOrderId().equals(payment.getGatewayOrderId()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pharmacy payment attempt not found"));
        if ("RECEIVED".equals(attempt.getStatus())) return invoice;
        if (!"RAZORPAY".equalsIgnoreCase(request.getProvider())
                || !"RAZORPAY".equals(attempt.getProvider())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment provider does not match the pharmacy checkout");
        }
        if (!StringUtils.hasText(request.getGatewayPaymentId()) || !StringUtils.hasText(request.getSignature())
                || !constantTimeEquals(hmacSha256(razorpaySecret,
                        request.getGatewayOrderId() + "|" + request.getGatewayPaymentId()), request.getSignature())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Razorpay payment signature could not be verified");
        }
        if (!"PENDING".equals(attempt.getStatus())
                || attempt.getAmount().compareTo(invoice.getBalanceDue()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pharmacy payment attempt is no longer payable");
        }
        attempt.setStatus("RECEIVED");
        attempt.setGatewayPaymentId(request.getGatewayPaymentId());
        applyPayment(invoice, attempt.getAmount());
        return saveInvoice(invoice);
    }

    public void requirePaidPrescription(MedicationPrescription prescription) {
        requirePaid(createInvoiceForPrescription(prescription));
    }

    public void requirePaidIssue(PrescriptionIssue issue) {
        requirePaid(createInvoiceForIssue(issue));
    }

    private PharmacyInvoice saveNewInvoice(
            String referenceKey, String referenceType, String referenceId,
            String patientId, String patientName, List<PharmacyInvoice.LineItem> items) {
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A pharmacy invoice must contain medicines");
        }
        PharmacyInvoice invoice = new PharmacyInvoice();
        invoice.setReferenceKey(referenceKey);
        invoice.setReferenceType(referenceType);
        invoice.setReferenceId(referenceId);
        invoice.setPatientId(patientId);
        invoice.setPatientName(patientName);
        invoice.setItems(items);
        BigDecimal total = items.stream().map(PharmacyInvoice.LineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        invoice.setAmount(total);
        invoice.setStatus(total.signum() == 0 ? "NO_CHARGE" : "PENDING");
        return invoiceRepository.save(invoice);
    }

    private PharmacyInvoice.LineItem invoiceLine(
            String medicationId, String name, String strength, String dosageForm, int quantity, BigDecimal price) {
        if (price == null || price.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A prescribed medicine has no valid pharmacy unit price");
        }
        PharmacyInvoice.LineItem line = new PharmacyInvoice.LineItem();
        line.setMedicationId(medicationId);
        line.setMedicationName(name);
        line.setStrength(strength);
        line.setDosageForm(dosageForm);
        line.setQuantity(quantity);
        line.setUnitPrice(price.setScale(2, RoundingMode.HALF_UP));
        line.setLineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
        return line;
    }

    private void requirePaid(PharmacyInvoice invoice) {
        if (!List.of("PAID", "NO_CHARGE").contains(invoice.getStatus()) || invoice.getBalanceDue().signum() > 0) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Collect the pharmacy invoice payment before dispensing medicines");
        }
    }

    private void applyPayment(PharmacyInvoice invoice, BigDecimal amount) {
        invoice.setPaidAmount(invoice.getPaidAmount().add(amount).setScale(2, RoundingMode.HALF_UP));
        invoice.setStatus(invoice.getBalanceDue().signum() == 0 ? "PAID" : "PARTIALLY_PAID");
    }

    private PharmacyInvoice findInvoice(String invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pharmacy invoice not found"));
    }

    private void expireStaleAttempts(PharmacyInvoice invoice) {
        Instant expiration = Instant.now().minus(java.time.Duration.ofMinutes(30));
        boolean expired = false;
        for (PharmacyInvoice.Payment payment : invoice.getPayments()) {
            if ("PENDING".equals(payment.getStatus()) && payment.getCreatedAt() != null
                    && payment.getCreatedAt().isBefore(expiration)) {
                payment.setStatus("EXPIRED");
                expired = true;
            }
        }
        if (expired) saveInvoice(invoice);
    }

    private PharmacyInvoice saveInvoice(PharmacyInvoice invoice) {
        try {
            return invoiceRepository.save(invoice);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pharmacy invoice changed during payment processing. Refresh and try again.", exception);
        }
    }

    private void requireRazorpayConfiguration() {
        if (!StringUtils.hasText(razorpayKeyId) || !StringUtils.hasText(razorpaySecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay is not configured. Set its payment credentials in the backend environment.");
        }
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not verify Razorpay payment signature", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private record RazorpayOrder(String id) {}
}
