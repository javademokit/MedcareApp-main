package com.pharmacy.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Dto {
    private Dto() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String token, String username, String fullName, List<String> roles) {}

    public record MedicineRequest(
            @NotBlank String brandName, String genericName, String manufacturer, String dosageForm,
            @Min(1) int unitsPerPack, String hsnCode, @NotNull BigDecimal gstPercent,
            @NotBlank String schedule, String storage, @Min(0) int reorderLevel, String barcode) {}

    public record SupplierRequest(@NotBlank String name, String gstin, String drugLicenceNo, String phone, String address) {}

    public record GrnItemReq(@NotBlank String medicineId, @NotBlank String batchNo, LocalDate mfgDate,
                             @NotNull LocalDate expiryDate, @Min(1) int packs, @Min(0) int freePacks,
                             @NotNull @DecimalMin("0.0") BigDecimal rate, @NotNull @DecimalMin("0.0") BigDecimal mrp) {}
    public record GrnRequest(@NotBlank String supplierId, @NotBlank String supplierInvoiceNo,
                             @NotNull LocalDate invoiceDate, @NotEmpty @Valid List<GrnItemReq> items) {}

    public record SaleItemReq(@NotBlank String medicineId, @Min(1) int qty, @DecimalMin("0.0") BigDecimal discountPercent) {}
    public record SaleRequest(String customerName, String customerPhone, String doctorName, String doctorRegNo,
                              boolean interState, @NotBlank String paymentMode,
                              @NotEmpty @Valid List<SaleItemReq> items) {}
    public record CancelRequest(@NotBlank String reason) {}

    public record AdjustRequest(@NotBlank String batchId, int qtyDelta, @NotBlank String reason) {}
    public record StatusRequest(@NotBlank String status) {}

    // ---- responses ----
    public record LookupRow(String medicineId, String brandName, String genericName, String schedule,
                            BigDecimal gstPercent, int unitsPerPack, int stock, BigDecimal mrp, LocalDate nearestExpiry) {}
    public record BatchView(String id, String medicineId, String medicineName, String batchNo, LocalDate mfgDate,
                            LocalDate expiryDate, BigDecimal mrp, BigDecimal purchaseRate, int qtyAvailable,
                            int unitsPerPack, String status) {}
    public record LowStockRow(String medicineId, String name, int stock, int reorderLevel) {}
    public record DashboardResponse(BigDecimal todaySales, int todayBills, long expiringSoon, long expired,
                                    List<LowStockRow> lowStock) {}
}
