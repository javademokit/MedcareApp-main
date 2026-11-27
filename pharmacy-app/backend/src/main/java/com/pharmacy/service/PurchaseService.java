package com.pharmacy.service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.pharmacy.common.ApiException;
import com.pharmacy.model.*;
import com.pharmacy.repo.Repos.*;
import com.pharmacy.web.Dto.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseService {
    private final SupplierRepository suppliers;
    private final MedicineRepository medicines;
    private final BatchRepository batches;
    private final GrnRepository grns;
    private final MongoTemplate mongo;
    private final AuditService audit;

    @Transactional
    public Grn createGrn(GrnRequest r) {
        Supplier s = suppliers.findById(r.supplierId()).orElseThrow(() -> ApiException.notFound("Supplier not found"));
        if (grns.existsBySupplierIdAndSupplierInvoiceNo(s.getId(), r.supplierInvoiceNo().trim()))
            throw ApiException.conflict("This supplier invoice number is already entered");
        LocalDate today = LocalDate.now(InventoryService.IST);
        String user = AuditService.currentUser();
        BigDecimal total = BigDecimal.ZERO;
        List<Grn.Item> items = new ArrayList<>();

        for (GrnItemReq it : r.items()) {
            Medicine m = medicines.findById(it.medicineId())
                    .orElseThrow(() -> ApiException.notFound("Medicine not found: " + it.medicineId()));
            if (!it.expiryDate().isAfter(today))
                throw ApiException.bad(m.getBrandName() + " batch " + it.batchNo() + " is already expired");
            int units = (it.packs() + it.freePacks()) * m.getUnitsPerPack();
            String batchNo = it.batchNo().trim().toUpperCase();

            Batch existing = batches.findByMedicineIdAndBatchNo(m.getId(), batchNo).orElse(null);
            String batchId;
            if (existing != null) {
                if (!existing.getExpiryDate().equals(it.expiryDate()))
                    throw ApiException.bad("Batch " + batchNo + " already exists with a different expiry date");
                mongo.updateFirst(query(where("id").is(existing.getId())),
                        new Update().inc("qtyAvailable", units).set("mrp", org.bson.types.Decimal128.parse(it.mrp().toPlainString()))
                                .set("purchaseRate", org.bson.types.Decimal128.parse(it.rate().toPlainString())), Batch.class);
                batchId = existing.getId();
            } else {
                batchId = batches.save(Batch.builder().medicineId(m.getId()).batchNo(batchNo).mfgDate(it.mfgDate())
                        .expiryDate(it.expiryDate()).purchaseRate(it.rate()).mrp(it.mrp()).qtyAvailable(units)
                        .supplierId(s.getId()).status("ACTIVE").build()).getId();
            }
            mongo.insert(StockMovement.builder().batchId(batchId).medicineId(m.getId()).type("PURCHASE").qty(units)
                    .refType("GRN").reason(r.supplierInvoiceNo()).at(LocalDateTime.now()).by(user).build());

            total = total.add(it.rate().multiply(BigDecimal.valueOf(it.packs())));
            items.add(Grn.Item.builder().medicineId(m.getId()).medicineName(m.getBrandName()).batchNo(batchNo)
                    .mfgDate(it.mfgDate()).expiryDate(it.expiryDate()).packs(it.packs()).freePacks(it.freePacks())
                    .rate(it.rate()).mrp(it.mrp()).build());
        }
        Grn g = grns.save(Grn.builder().supplierId(s.getId()).supplierName(s.getName())
                .supplierInvoiceNo(r.supplierInvoiceNo().trim()).invoiceDate(r.invoiceDate()).items(items)
                .total(total.setScale(2, java.math.RoundingMode.HALF_UP)).createdAt(LocalDateTime.now()).createdBy(user).build());
        audit.log("GRN", "Grn", g.getId(), s.getName() + " / " + g.getSupplierInvoiceNo());
        return g;
    }
}
