package com.example.MedcareApp.Entity.billing;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DischargePayment {
    private String id;
    private String method;
    private String status;
    private BigDecimal amount;
    private String transactionReference;
    private Instant createdAt;
    private Instant confirmedAt;
}
