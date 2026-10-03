package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.billing.AppointmentInvoice;
import com.example.MedcareApp.Interafce.AppointmentInvoiceRepository;
import com.example.MedcareApp.web.PaymentVerificationRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class PaymentGatewayService {
    private final AppointmentInvoiceRepository invoiceRepository;
    private final RestClient.Builder restClientBuilder;

    @Value("${PAYMENTS_RAZORPAY_KEY_ID:}") private String razorpayKeyId;
    @Value("${PAYMENTS_RAZORPAY_KEY_SECRET:}") private String razorpaySecret;
    @Value("${PAYMENTS_STRIPE_SECRET_KEY:}") private String stripeSecret;
    @Value("${PAYMENTS_PAYU_KEY:}") private String payuKey;
    @Value("${PAYMENTS_PAYU_SALT:}") private String payuSalt;
    @Value("${PAYMENTS_PAYU_MODE:test}") private String payuMode;
    @Value("${PAYMENTS_PUBLIC_BACKEND_URL:http://localhost:7771}") private String publicBackendUrl;
    @Value("${PAYMENTS_FRONTEND_URL:http://localhost:3000}") private String frontendUrl;

    public Map<String, Object> configuredGateways() {
        return Map.of(
                "RAZORPAY", hasText(razorpayKeyId) && hasText(razorpaySecret),
                "STRIPE", hasText(stripeSecret),
                "PAYU", hasText(payuKey) && hasText(payuSalt));
    }

    public Map<String, Object> createCheckout(String invoiceId, String requestedProvider) {
        String provider = requestedProvider.trim().toUpperCase(Locale.ROOT);
        AppointmentInvoice invoice = findInvoice(invoiceId);
        if (invoice.getBalanceDue().signum() <= 0 || List.of("VOID", "REFUND_REQUIRED").contains(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice has no payable balance");
        }
        expireStaleAttempts(invoice);
        if (invoice.getPayments().stream().anyMatch(payment -> "PENDING".equals(payment.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A payment attempt is already pending for this invoice");
        }
        AppointmentInvoice.Payment attempt = new AppointmentInvoice.Payment();
        attempt.setAmount(invoice.getBalanceDue().setScale(2, RoundingMode.HALF_UP));
        attempt.setStatus("PENDING");
        attempt.setProvider(provider);
        attempt.setMethod(provider);
        try {
            Map<String, Object> checkout = switch (provider) {
                case "RAZORPAY" -> createRazorpayCheckout(invoice, attempt);
                case "STRIPE" -> createStripeCheckout(invoice, attempt);
                case "PAYU" -> createPayuCheckout(invoice, attempt);
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment provider");
            };
            invoice.getPayments().add(attempt);
            saveInvoice(invoice);
            return checkout;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not create a payment request with " + provider, exception);
        }
    }

    public AppointmentInvoice verifyCheckout(String invoiceId, PaymentVerificationRequest request) {
        AppointmentInvoice invoice = findInvoice(invoiceId);
        AppointmentInvoice.Payment attempt = invoice.getPayments().stream()
                .filter(payment -> request.getGatewayOrderId().equals(payment.getGatewayOrderId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment attempt not found"));
        if ("RECEIVED".equals(attempt.getStatus())) return invoice;
        String provider = request.getProvider().trim().toUpperCase(Locale.ROOT);
        if (!provider.equals(attempt.getProvider())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment provider does not match the checkout");
        }
        boolean verified = switch (provider) {
            case "RAZORPAY" -> verifyRazorpay(request);
            case "STRIPE" -> verifyStripe(attempt, invoice);
            case "PAYU" -> false;
            default -> false;
        };
        if (!verified) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment has not been verified by the provider");
        }
        markReceived(invoice, attempt, request.getGatewayPaymentId());
        return saveInvoice(invoice);
    }

    public String handlePayuCallback(Map<String, String> fields) {
        String txnId = fields.get("txnid");
        AppointmentInvoice invoice = txnId == null ? null : invoiceRepository.findByPaymentsGatewayOrderId(txnId)
                .orElse(null);
        if (invoice == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PayU payment attempt not found");
        }
        AppointmentInvoice.Payment attempt = invoice.getPayments().stream()
                .filter(payment -> txnId.equals(payment.getGatewayOrderId()))
                .findFirst().orElseThrow();
        boolean verified = verifyPayu(fields, invoice);
        if (verified && "success".equalsIgnoreCase(fields.get("status"))) {
            if (!"RECEIVED".equals(attempt.getStatus())) {
                markReceived(invoice, attempt, fields.get("mihpayid"));
                saveInvoice(invoice);
            }
        } else if (verified) {
            attempt.setStatus("FAILED");
            saveInvoice(invoice);
        }
        return frontendUrl + "/HospitalDashboard?billing=1&paymentResult="
                + (verified && "success".equalsIgnoreCase(fields.get("status")) ? "success" : "failed");
    }

    private Map<String, Object> createRazorpayCheckout(
            AppointmentInvoice invoice, AppointmentInvoice.Payment attempt) {
        requireConfigured(hasText(razorpayKeyId) && hasText(razorpaySecret), "Razorpay");
        Map<String, Object> body = Map.of(
                "amount", attempt.getAmount().movePointRight(2).intValueExact(),
                "currency", invoice.getCurrency(),
                "receipt", invoice.getInvoiceNumber());
        RazorpayOrder response = restClientBuilder.build().post().uri("https://api.razorpay.com/v1/orders")
                .headers(headers -> headers.setBasicAuth(razorpayKeyId, razorpaySecret))
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(RazorpayOrder.class);
        if (response == null || !hasText(response.id())) {
            throw new IllegalStateException("Razorpay did not return an order ID");
        }
        attempt.setGatewayOrderId(response.id());
        return Map.of("provider", "RAZORPAY", "keyId", razorpayKeyId, "orderId", response.id(),
                "amount", body.get("amount"), "currency", invoice.getCurrency(),
                "name", invoice.getPatientName(), "description", "Consultation " + invoice.getInvoiceNumber());
    }

    private Map<String, Object> createStripeCheckout(
            AppointmentInvoice invoice, AppointmentInvoice.Payment attempt) {
        requireConfigured(hasText(stripeSecret), "Stripe");
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("mode", "payment");
        body.add("success_url", frontendUrl + "/HospitalDashboard?billing=1&invoiceId="
                + invoice.getId() + "&stripeSession={CHECKOUT_SESSION_ID}");
        body.add("cancel_url", frontendUrl + "/HospitalDashboard?billing=1&paymentResult=cancelled");
        body.add("client_reference_id", invoice.getId());
        body.add("metadata[invoice_id]", invoice.getId());
        body.add("line_items[0][quantity]", "1");
        body.add("line_items[0][price_data][currency]", invoice.getCurrency().toLowerCase(Locale.ROOT));
        body.add("line_items[0][price_data][unit_amount]",
                attempt.getAmount().movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString());
        body.add("line_items[0][price_data][product_data][name]", "Consultation " + invoice.getInvoiceNumber());
        StripeSession response = restClientBuilder.build().post().uri("https://api.stripe.com/v1/checkout/sessions")
                .headers(headers -> headers.setBasicAuth(stripeSecret, ""))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body)
                .retrieve().body(StripeSession.class);
        if (response == null || !hasText(response.id()) || !hasText(response.url())) {
            throw new IllegalStateException("Stripe did not return a checkout session");
        }
        attempt.setGatewayOrderId(response.id());
        return Map.of("provider", "STRIPE", "sessionId", response.id(), "checkoutUrl", response.url());
    }

    private Map<String, Object> createPayuCheckout(
            AppointmentInvoice invoice, AppointmentInvoice.Payment attempt) {
        requireConfigured(hasText(payuKey) && hasText(payuSalt), "PayU");
        if (!hasText(invoice.getPatientEmail()) || !hasText(invoice.getPatientMobile())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A patient email and mobile number are required for PayU checkout");
        }
        String txnId = UUID.randomUUID().toString().replace("-", "");
        String amount = attempt.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        String name = invoice.getPatientName() == null ? "Patient" : invoice.getPatientName();
        String email = invoice.getPatientEmail() == null ? "" : invoice.getPatientEmail();
        String phone = invoice.getPatientMobile() == null ? "" : invoice.getPatientMobile();
        String product = "Consultation " + invoice.getInvoiceNumber();
        String hashInput = String.join("|", payuKey, txnId, amount, product, name, email,
                invoice.getId(), "", "", "", "", "", "", "", "", payuSalt);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", payuKey);
        fields.put("txnid", txnId);
        fields.put("amount", amount);
        fields.put("productinfo", product);
        fields.put("firstname", name);
        fields.put("email", email);
        fields.put("phone", phone);
        fields.put("udf1", invoice.getId());
        fields.put("surl", publicBackendUrl + "/api/billing/appointment-invoices/gateway/payu/callback");
        fields.put("furl", publicBackendUrl + "/api/billing/appointment-invoices/gateway/payu/callback");
        fields.put("hash", sha512(hashInput));
        attempt.setGatewayOrderId(txnId);
        String action = "live".equalsIgnoreCase(payuMode)
                ? "https://secure.payu.in/_payment"
                : "https://test.payu.in/_payment";
        return Map.of("provider", "PAYU", "action", action, "fields", fields);
    }

    private boolean verifyRazorpay(PaymentVerificationRequest request) {
        if (!hasText(razorpaySecret) || !hasText(request.getGatewayPaymentId()) || !hasText(request.getSignature())) return false;
        return constantTimeEquals(hmacSha256(razorpaySecret,
                request.getGatewayOrderId() + "|" + request.getGatewayPaymentId()), request.getSignature());
    }

    private boolean verifyStripe(AppointmentInvoice.Payment attempt, AppointmentInvoice invoice) {
        if (!hasText(stripeSecret)) return false;
        StripeSession response = restClientBuilder.build().get()
                .uri("https://api.stripe.com/v1/checkout/sessions/{id}", attempt.getGatewayOrderId())
                .headers(headers -> headers.setBasicAuth(stripeSecret, ""))
                .retrieve().body(StripeSession.class);
        return response != null && attempt.getGatewayOrderId().equals(response.id())
                && "paid".equals(response.paymentStatus())
                && invoice.getId().equals(response.clientReferenceId());
    }

    private boolean verifyPayu(Map<String, String> fields, AppointmentInvoice invoice) {
        if (!hasText(payuSalt)) return false;
        String hashInput = String.join("|", payuSalt, fields.getOrDefault("status", ""),
                "", "", "", "", "", "", "", "", invoice.getId(), fields.getOrDefault("email", ""),
                fields.getOrDefault("firstname", ""), fields.getOrDefault("productinfo", ""),
                fields.getOrDefault("amount", ""), fields.getOrDefault("txnid", ""), payuKey);
        return invoice.getId().equals(fields.get("udf1"))
                && constantTimeEquals(sha512(hashInput), fields.getOrDefault("hash", ""));
    }

    private void markReceived(AppointmentInvoice invoice, AppointmentInvoice.Payment attempt, String paymentId) {
        if (!List.of("PENDING", "EXPIRED").contains(attempt.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment attempt is not pending");
        }
        if (attempt.getAmount().compareTo(invoice.getBalanceDue()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment exceeds the remaining invoice balance");
        }
        attempt.setStatus("RECEIVED");
        attempt.setGatewayPaymentId(paymentId);
        invoice.setPaidAmount(invoice.getPaidAmount().add(attempt.getAmount()));
        invoice.setStatus(invoice.getBalanceDue().signum() == 0 ? "PAID" : "PARTIALLY_PAID");
    }

    private AppointmentInvoice findInvoice(String id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment invoice not found"));
    }

    private void expireStaleAttempts(AppointmentInvoice invoice) {
        java.time.Instant expiration = java.time.Instant.now().minus(java.time.Duration.ofMinutes(30));
        boolean expired = false;
        for (AppointmentInvoice.Payment payment : invoice.getPayments()) {
            if ("PENDING".equals(payment.getStatus()) && payment.getCreatedAt() != null
                    && payment.getCreatedAt().isBefore(expiration)) {
                payment.setStatus("EXPIRED");
                expired = true;
            }
        }
        if (expired) saveInvoice(invoice);
    }

    private AppointmentInvoice saveInvoice(AppointmentInvoice invoice) {
        try {
            return invoiceRepository.save(invoice);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invoice changed during payment processing. Refresh and try again.", exception);
        }
    }

    private void requireConfigured(boolean configured, String provider) {
        if (!configured) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    provider + " is not configured. Set its payment credentials in the backend environment.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not verify payment signature", exception);
        }
    }

    private String sha512(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create PayU payment signature", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private record RazorpayOrder(String id) {}
    private record StripeSession(
            String id,
            String url,
            @JsonProperty("payment_status") String paymentStatus,
            @JsonProperty("client_reference_id") String clientReferenceId) {}
}
