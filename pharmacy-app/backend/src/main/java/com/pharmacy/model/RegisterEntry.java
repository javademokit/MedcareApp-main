package com.pharmacy.model;

import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Statutory register line (Schedule H1 / X-NDPS). Append-only: never updated or deleted by the app. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document("registerEntries")
public class RegisterEntry {
    @Id private String id;
    @Indexed private String register; // H1 or X
    private String saleId;
    private String invoiceNo;
    private String medicineId;
    private String medicineName;
    private String batchNo;
    private int qty;
    private String patientName;
    private String doctorName;
    private String doctorRegNo;
    private String dispensedBy;
    private String pharmacistRegNo;
    @Indexed private LocalDateTime at;
}
