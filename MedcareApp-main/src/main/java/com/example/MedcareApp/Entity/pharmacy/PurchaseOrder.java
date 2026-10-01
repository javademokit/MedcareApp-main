package com.example.MedcareApp.Entity.pharmacy;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Document(collection = "pharmacy_purchase_orders")
public class PurchaseOrder {
    @Id
    private String id;
    @NotBlank
    private String medicationId;
    @NotBlank
    private String medicationName;
    @NotBlank
    private String supplier;
    @Min(1)
    private int quantity;
    private String expectedDeliveryDate;
    private String status = "ORDERED";
    private Instant createdAt;
    private Instant receivedAt;
}
