package com.pharmacy.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document("sales")
public class Sale {
    @Id private String id;
    @Indexed(unique = true) private String invoiceNo;
    @Indexed private LocalDateTime invoiceDate;
    private String customerName;
    private String customerPhone;
    private String doctorName;
    private String doctorRegNo;
    private boolean interState;
    private List<Line> items;
    @Field(targetType = FieldType.DECIMAL128) private BigDecimal taxable;
    @Field(targetType = FieldType.DECIMAL128) private BigDecimal cgst;
    @Field(targetType = FieldType.DECIMAL128) private BigDecimal sgst;
    @Field(targetType = FieldType.DECIMAL128) private BigDecimal igst;
    @Field(targetType = FieldType.DECIMAL128) private BigDecimal roundOff;
    @Field(targetType = FieldType.DECIMAL128) private BigDecimal grandTotal;
    private String paymentMode;
    /** COMPLETED, CANCELLED */
    private String status;
    private String cancelReason;
    private String cancelledBy;
    private String createdBy;
    private String pharmacistRegNo;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Line {
        private String medicineId;
        private String batchId;
        private String name;
        private String schedule;
        private String batchNo;
        private LocalDate expiryDate;
        private String hsn;
        @Field(targetType = FieldType.DECIMAL128) private BigDecimal gstPercent;
        @Field(targetType = FieldType.DECIMAL128) private BigDecimal mrp;        // per pack
        @Field(targetType = FieldType.DECIMAL128) private BigDecimal unitPrice;  // per loose unit
        private int qty;
        @Field(targetType = FieldType.DECIMAL128) private BigDecimal discountPercent;
        @Field(targetType = FieldType.DECIMAL128) private BigDecimal taxable;
        @Field(targetType = FieldType.DECIMAL128) private BigDecimal cgst;
        @Field(targetType = FieldType.DECIMAL128) private BigDecimal sgst;
        @Field(targetType = FieldType.DECIMAL128) private BigDecimal igst;
        @Field(targetType = FieldType.DECIMAL128) private BigDecimal total;
    }
}
