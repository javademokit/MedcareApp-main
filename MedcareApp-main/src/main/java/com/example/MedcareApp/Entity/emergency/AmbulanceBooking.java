package com.example.MedcareApp.Entity.emergency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "ambulance_bookings")
public class AmbulanceBooking {
    @Id
    private String id = UUID.randomUUID().toString();
    private String bookingNumber = "AMB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    @Version
    private Long version;
    private String patientId;
    private String patientName;
    private String patientMobile;
    private String branchId;
    private String branchName;
    private String pickupAddress;
    private String dropAddress;
    private BigDecimal distanceKm;
    private BigDecimal ratePerKm;
    private BigDecimal amount;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private String currency = "INR";
    private String paymentMethod;
    private String paymentStatus = "PENDING";
    private String status = "AWAITING_PAYMENT";
    private String vehicleId;
    private String vehicleRegistration;
    private String driverName;
    private String driverPhone;
    private String currentLocation;
    private String trackingNote;
    private String refundStatus = "NOT_REQUIRED";
    private BigDecimal refundAmount = BigDecimal.ZERO;
    private String refundedBy;
    private Instant refundedAt;
    private String createdBy;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private List<Payment> payments = new ArrayList<>();

    @Data
    public static class Payment {
        private String id = UUID.randomUUID().toString();
        private BigDecimal amount;
        private String method;
        private String status = "RECEIVED";
        private String provider;
        private String gatewayOrderId;
        private String gatewayPaymentId;
        private String gatewayRefundId;
        private Instant createdAt = Instant.now();
        private Instant receivedAt;
        private String receivedBy;
    }
}
