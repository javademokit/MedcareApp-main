package com.pharmacy.model;

import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Append-only ledger of every stock change. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document("stockMovements")
public class StockMovement {
    @Id private String id;
    private String batchId;
    private String medicineId;
    /** PURCHASE, SALE, SALE_CANCEL, ADJUSTMENT */
    private String type;
    private int qty; // +in / -out (loose units)
    private String refType;
    private String refId;
    private String reason;
    private LocalDateTime at;
    private String by;
}
