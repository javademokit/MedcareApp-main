package com.pharmacy.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document("grns")
@CompoundIndex(name = "supplier_invoice", def = "{'supplierId':1,'supplierInvoiceNo':1}", unique = true)
public class Grn {
    @Id private String id;
    private String supplierId;
    private String supplierName;
    private String supplierInvoiceNo;
    private LocalDate invoiceDate;
    private List<Item> items;
    @Field(targetType = FieldType.DECIMAL128) private BigDecimal total;
    private LocalDateTime createdAt;
    private String createdBy;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        private String medicineId;
        private String medicineName;
        private String batchNo;
        private LocalDate mfgDate;
        private LocalDate expiryDate;
        private int packs;
        private int freePacks;
        @Field(targetType = FieldType.DECIMAL128) private BigDecimal rate;
        @Field(targetType = FieldType.DECIMAL128) private BigDecimal mrp;
    }
}
