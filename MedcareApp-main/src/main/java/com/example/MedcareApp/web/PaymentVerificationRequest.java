package com.example.MedcareApp.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaymentVerificationRequest {
    @NotBlank private String provider;
    @NotBlank private String gatewayOrderId;
    private String gatewayPaymentId;
    private String signature;
}
