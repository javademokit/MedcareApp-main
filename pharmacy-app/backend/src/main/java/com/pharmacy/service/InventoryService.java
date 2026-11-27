package com.pharmacy.service;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.pharmacy.common.ApiException;
import com.pharmacy.model.Batch;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.StockMovement;
import com.pharmacy.repo.Repos.MedicineRepository;
import com.pharmacy.web.Dto.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final Set<String> BATCH_STATUSES = Set.of("ACTIVE", "QUARANTINE", "RECALLED");
    private final MongoTemplate mongo;
    private final MedicineRepository medicines;
    private final AuditService audit;

    @Data public static class StockRow { private String id; private int qty; }

    /** Sellable stock (ACTIVE, not expired) per medicine, in loose units. */
    public Map<String, Integer> stockByMedicine() {
        Aggregation agg = newAggregation(
                match(where("status").is("ACTIVE").and("expiryDate").gt(LocalDate.now(IST))),
                group("medicineId").sum("qtyAvailable").as("qty"));
        Map<String, Integer> out = new HashMap<>();
        for (StockRow r : mongo.aggregate(agg, Batch.class, StockRow.class).getMappedResults()) out.put(r.getId(), r.getQty());
        return out;
    }

    public List<LowStockRow> lowStock() {
        Map<String, Integer> stock = stockByMedicine();
        return medicines.findAll().stream()
                .filter(m -> m.isActive() && m.getReorderLevel() > 0)
                .map(m -> new LowStockRow(m.getId(), m.getBrandName(), stock.getOrDefault(m.getId(), 0), m.getReorderLevel()))
                .filter(r -> r.stock() < r.reorderLevel())
                .sorted(Comparator.comparingInt(LowStockRow::stock)).toList();
    }

    /** Batches with stock. days != null -> only those expiring within N days (expired ones included). */
    public List<BatchView> batches(String medicineId, Integer days) {
        Criteria c = where("qtyAvailable").gt(0);
        if (medicineId != null && !medicineId.isBlank()) c.and("medicineId").is(medicineId);
        if (days != null) c.and("expiryDate").lte(LocalDate.now(IST).plusDays(days));
        List<Batch> list = mongo.find(query(c).with(Sort.by("expiryDate")).limit(300), Batch.class);
        Map<String, Medicine> meds = medicines.findAllById(list.stream().map(Batch::getMedicineId).distinct().toList())
                .stream().collect(Collectors.toMap(Medicine::getId, m -> m));
        return list.stream().map(b -> {
            Medicine m = meds.get(b.getMedicineId());
            return new BatchView(b.getId(), b.getMedicineId(), m == null ? "?" : m.getBrandName(), b.getBatchNo(),
                    b.getMfgDate(), b.getExpiryDate(), b.getMrp(), b.getPurchaseRate(), b.getQtyAvailable(),
                    m == null ? 1 : m.getUnitsPerPack(), b.getStatus());
        }).toList();
    }

    @Transactional
    public void adjust(AdjustRequest r) {
        if (r.qtyDelta() == 0) throw ApiException.bad("Quantity change cannot be zero");
        var q = where("id").is(r.batchId());
        if (r.qtyDelta() < 0) q = q.and("qtyAvailable").gte(-r.qtyDelta());
        Batch b = mongo.findAndModify(query(q), new Update().inc("qtyAvailable", r.qtyDelta()),
                FindAndModifyOptions.options().returnNew(true), Batch.class);
        if (b == null) throw ApiException.bad("Batch not found or not enough stock for this reduction");
        mongo.insert(StockMovement.builder().batchId(b.getId()).medicineId(b.getMedicineId()).type("ADJUSTMENT")
                .qty(r.qtyDelta()).refType("ADJUSTMENT").reason(r.reason()).at(LocalDateTime.now())
                .by(AuditService.currentUser()).build());
        audit.log("ADJUST_STOCK", "Batch", b.getId(), r.qtyDelta() + " units: " + r.reason());
    }

    public void setStatus(String batchId, String status) {
        if (!BATCH_STATUSES.contains(status)) throw ApiException.bad("Status must be one of " + BATCH_STATUSES);
        var res = mongo.updateFirst(query(where("id").is(batchId)), new Update().set("status", status), Batch.class);
        if (res.getMatchedCount() == 0) throw ApiException.notFound("Batch not found");
        audit.log("BATCH_STATUS", "Batch", batchId, status);
    }
}
