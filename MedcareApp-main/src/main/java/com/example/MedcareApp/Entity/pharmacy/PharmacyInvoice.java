package com.example.MedcareApp.Entity.pharmacy;

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
@Document(collection = "pharmacy_invoices")
public class PharmacyInvoice {
    @Id
    private String id = UUID.randomUUID().toString();
    private String invoiceNumber = "PH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    @Indexed(unique = true)
    private String referenceKey;
    private String referenceType;
    private String referenceId;
    @Version
    private Long version;
    private String patientId;
    private String patientName;
    private BigDecimal amount = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private String currency = "INR";
    private String status = "PENDING";
    private Instant createdAt = Instant.now();
    private List<LineItem> items = new ArrayList<>();
    private List<Payment> payments = new ArrayList<>();

    public BigDecimal getBalanceDue() {
        return amount.subtract(paidAmount).max(BigDecimal.ZERO);
    }

    @Data
    public static class LineItem {
        private String medicationId;
        private String medicationName;
        private String strength;
        private String dosageForm;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
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
