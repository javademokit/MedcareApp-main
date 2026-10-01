package com.example.MedcareApp.Entity.pharmacy;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Document(collection = "pharmacy_inventory")
public class MedicationItem {
    @Id
    private String id;
    @NotBlank
    private String name;
    private String strength;
    private String dosageForm;
    @NotBlank
    private String batchNumber;
    @Min(0)
    private int quantityOnHand;
    @Min(0)
    private int reorderLevel;
    @NotNull
    private BigDecimal unitPrice;
    private String supplier;
    private String location;
    private String expiryDate;
    private Instant createdAt;
    private Instant updatedAt;
}
