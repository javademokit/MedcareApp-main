package com.pharmacy.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document("batches")
@CompoundIndex(name = "med_batch", def = "{'medicineId':1,'batchNo':1}", unique = true)
public class Batch {
    @Id private String id;
    @Indexed private String medicineId;
    private String batchNo;
    private LocalDate mfgDate;
    @Indexed private LocalDate expiryDate;
    @Field(targetType = FieldType.DECIMAL128) private BigDecimal purchaseRate; // per pack
    @Field(targetType = FieldType.DECIMAL128) private BigDecimal mrp;          // per pack
    /** Quantity in loose units */
    private int qtyAvailable;
    private String supplierId;
    /** ACTIVE, QUARANTINE, RECALLED */
    private String status;
}
