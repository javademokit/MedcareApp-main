package com.example.MedcareApp.Entity.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "appointment_invoices")
public class AppointmentInvoice {
    @Id
    private String id = UUID.randomUUID().toString();
    private String invoiceNumber = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    @Indexed(unique = true)
    private String appointmentId;
    @Version
    private Long version;
    private String patientId;
    private String patientName;
    private String patientEmail;
    private String patientMobile;
    private String doctorName;
    private String service = "Consultation";
    private BigDecimal amount = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private String currency = "INR";
    private String status = "PENDING";
    private Instant createdAt = Instant.now();
    private List<Payment> payments = new ArrayList<>();

    public BigDecimal getBalanceDue() {
        if ("VOID".equals(status) || "REFUND_REQUIRED".equals(status)) return BigDecimal.ZERO.setScale(2);
        return amount.subtract(paidAmount).max(BigDecimal.ZERO);
    }

    @Data
    public static class Payment {
        private String id = UUID.randomUUID().toString();
        private BigDecimal amount;
        private String method;
        private String reference;
        private String status = "RECEIVED";
        private String provider;
        private String gatewayOrderId;
        private String gatewayPaymentId;
        private Instant createdAt = Instant.now();
        private String receivedBy;
    }
}
