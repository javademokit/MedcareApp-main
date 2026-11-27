package com.pharmacy.service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.pharmacy.model.Batch;
import com.pharmacy.model.Sale;
import com.pharmacy.web.Dto.DashboardResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final MongoTemplate mongo;
    private final InventoryService inventory;

    public DashboardResponse summary() {
        LocalDate today = LocalDate.now(InventoryService.IST);
        LocalDateTime start = today.atStartOfDay();
        List<Sale> todays = mongo.find(query(where("invoiceDate").gte(start).lt(start.plusDays(1)).and("status").is("COMPLETED")), Sale.class);
        BigDecimal total = todays.stream().map(Sale::getGrandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        long expired = mongo.count(query(where("qtyAvailable").gt(0).and("expiryDate").lte(today)), Batch.class);
        long soon = mongo.count(query(where("qtyAvailable").gt(0).and("expiryDate").gt(today).lte(today.plusDays(90))), Batch.class);
        return new DashboardResponse(total, todays.size(), soon, expired, inventory.lowStock());
    }
}
