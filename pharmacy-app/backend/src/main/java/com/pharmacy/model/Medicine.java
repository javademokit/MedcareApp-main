package com.pharmacy.model;

import java.math.BigDecimal;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document("medicines")
public class Medicine {
    @Id private String id;
    private String brandName;
    private String genericName;
    private String manufacturer;
    private String dosageForm;
    /** Loose units per pack (tablets per strip, ml per bottle ...). Stock is tracked in loose units. */
    private int unitsPerPack;
    private String hsnCode;
    @Field(targetType = FieldType.DECIMAL128) private BigDecimal gstPercent;
    /** OTC, G, H, H1, X, NDPS */
    private String schedule;
    /** ROOM, COLD, PROTECT_LIGHT */
    private String storage;
    private int reorderLevel;
    private String barcode;
    private boolean active;
}
