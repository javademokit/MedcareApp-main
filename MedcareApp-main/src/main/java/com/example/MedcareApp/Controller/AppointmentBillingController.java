package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.billing.AppointmentInvoice;
import com.example.MedcareApp.services.AppointmentBillingService;
import com.example.MedcareApp.services.PaymentGatewayService;
import com.example.MedcareApp.web.AppointmentPaymentRequest;
import com.example.MedcareApp.web.PaymentCheckoutRequest;
import com.example.MedcareApp.web.PaymentVerificationRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import java.util.Map;

@RestController
@RequestMapping("/api/billing/appointment-invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','RECEPTIONIST','CRM_EXECUTIVE','BILLING_EXECUTIVE','FINANCE')")
public class AppointmentBillingController {
    private final AppointmentBillingService billingService;
    private final PaymentGatewayService paymentGatewayService;

    @GetMapping
    public List<AppointmentInvoice> getInvoices() {
        return billingService.getInvoices();
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentInvoice recordPayment(
            @PathVariable String id, @Valid @RequestBody AppointmentPaymentRequest request, Principal principal) {
        return billingService.recordPayment(id, request, principal.getName());
    }

    @GetMapping("/gateways")
    public Map<String, Object> getGateways() {
        return paymentGatewayService.configuredGateways();
    }

    @PostMapping("/{id}/checkout")
    public Map<String, Object> createCheckout(
            @PathVariable String id, @Valid @RequestBody PaymentCheckoutRequest request) {
        return paymentGatewayService.createCheckout(id, request.getProvider());
    }

    @PostMapping("/{id}/verify")
    public AppointmentInvoice verifyCheckout(
            @PathVariable String id, @Valid @RequestBody PaymentVerificationRequest request) {
        return paymentGatewayService.verifyCheckout(id, request);
    }

    @PostMapping("/gateway/payu/callback")
    @PreAuthorize("permitAll()")
    public RedirectView payuCallback(@RequestParam Map<String, String> fields) {
        return new RedirectView(paymentGatewayService.handlePayuCallback(fields));
    }
}
