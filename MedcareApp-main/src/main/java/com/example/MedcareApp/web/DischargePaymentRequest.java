package com.example.MedcareApp.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class DischargePaymentRequest {
    @NotBlank private String method;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
    private String transactionReference;
}
