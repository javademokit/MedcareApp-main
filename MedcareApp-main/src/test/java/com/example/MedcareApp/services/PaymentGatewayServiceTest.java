package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.billing.AppointmentInvoice;
import com.example.MedcareApp.Interafce.AppointmentInvoiceRepository;
import com.example.MedcareApp.web.PaymentVerificationRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {
    @Mock private AppointmentInvoiceRepository invoiceRepository;
    @Mock private RestClient.Builder restClientBuilder;
    @InjectMocks private PaymentGatewayService paymentGatewayService;

    @Test
    void razorpayPaymentRequiresAndVerifiesProviderSignatureBeforeUpdatingInvoice() throws Exception {
        String secret = "test-secret";
        ReflectionTestUtils.setField(paymentGatewayService, "razorpaySecret", secret);
        AppointmentInvoice invoice = pendingInvoice();
        when(invoiceRepository.findById("invoice-1")).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(AppointmentInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentVerificationRequest request = new PaymentVerificationRequest();
        request.setProvider("RAZORPAY");
        request.setGatewayOrderId("order-1");
        request.setGatewayPaymentId("payment-1");
        request.setSignature(hmac(secret, "order-1|payment-1"));

        AppointmentInvoice paid = paymentGatewayService.verifyCheckout("invoice-1", request);

        assertEquals("PAID", paid.getStatus());
        assertEquals(new BigDecimal("500.00"), paid.getPaidAmount());
        assertEquals("payment-1", paid.getPayments().get(0).getGatewayPaymentId());
    }

    @Test
    void invalidGatewaySignatureDoesNotUpdateInvoice() {
        ReflectionTestUtils.setField(paymentGatewayService, "razorpaySecret", "test-secret");
        AppointmentInvoice invoice = pendingInvoice();
        when(invoiceRepository.findById("invoice-1")).thenReturn(Optional.of(invoice));
        PaymentVerificationRequest request = new PaymentVerificationRequest();
        request.setProvider("RAZORPAY");
        request.setGatewayOrderId("order-1");
        request.setGatewayPaymentId("payment-1");
        request.setSignature("invalid");

        assertThrows(ResponseStatusException.class,
                () -> paymentGatewayService.verifyCheckout("invoice-1", request));
        verify(invoiceRepository, never()).save(any(AppointmentInvoice.class));
    }

    @Test
    void checkoutRefusesToStartWhenGatewayCredentialsAreMissing() {
        AppointmentInvoice invoice = pendingInvoice();
        invoice.getPayments().clear();
        when(invoiceRepository.findById("invoice-1")).thenReturn(Optional.of(invoice));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> paymentGatewayService.createCheckout("invoice-1", "RAZORPAY"));

        assertEquals(503, exception.getStatusCode().value());
        verify(invoiceRepository, never()).save(any(AppointmentInvoice.class));
    }

    private AppointmentInvoice pendingInvoice() {
        AppointmentInvoice invoice = new AppointmentInvoice();
        invoice.setId("invoice-1");
        invoice.setAmount(new BigDecimal("500.00"));
        invoice.setPaidAmount(BigDecimal.ZERO.setScale(2));
        invoice.setStatus("PENDING");
        AppointmentInvoice.Payment attempt = new AppointmentInvoice.Payment();
        attempt.setProvider("RAZORPAY");
        attempt.setGatewayOrderId("order-1");
        attempt.setAmount(new BigDecimal("500.00"));
        attempt.setStatus("PENDING");
        invoice.getPayments().add(attempt);
        return invoice;
    }

    private String hmac(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
