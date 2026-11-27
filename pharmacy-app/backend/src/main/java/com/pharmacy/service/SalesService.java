package com.pharmacy.service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.pharmacy.common.ApiException;
import com.pharmacy.model.*;
import com.pharmacy.repo.Repos.*;
import com.pharmacy.web.Dto.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesService {
    private static final Set<String> RESTRICTED = Set.of("H", "H1", "X", "NDPS");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MedicineRepository medicines;
    private final SaleRepository sales;
    private final RegisterRepository registers;
    private final UserRepository users;
    private final MedicineService medicineService;
    private final MongoTemplate mongo;
    private final AuditService audit;

    @Value("${pharmacy.max-discount-percent:10}") private int maxDiscount;
    @Value("${pharmacy.manager-max-discount-percent:50}") private int managerMaxDiscount;

    private static boolean has(Authentication a, String role) {
        return a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_" + role));
    }

    /** POS search: medicine + sellable stock + MRP and expiry of the batch that FEFO will pick first. */
    public List<LookupRow> lookup(String q) {
        List<LookupRow> rows = new ArrayList<>();
        for (Medicine m : medicineService.search(q, 15)) {
            List<Batch> bs = sellableBatches(m.getId());
            int stock = bs.stream().mapToInt(Batch::getQtyAvailable).sum();
            Batch first = bs.isEmpty() ? null : bs.get(0);
            rows.add(new LookupRow(m.getId(), m.getBrandName(), m.getGenericName(), m.getSchedule(), m.getGstPercent(),
                    m.getUnitsPerPack(), stock, first == null ? null : first.getMrp(), first == null ? null : first.getExpiryDate()));
        }
        return rows;
    }

    private List<Batch> sellableBatches(String medicineId) {
        return mongo.find(query(where("medicineId").is(medicineId).and("status").is("ACTIVE")
                .and("expiryDate").gt(LocalDate.now(InventoryService.IST)).and("qtyAvailable").gt(0))
                .with(Sort.by("expiryDate")), Batch.class); // FEFO
    }

    @Transactional
    public Sale create(SaleRequest req, Authentication auth) {
        String user = auth.getName();
        boolean pharmacist = has(auth, "PHARMACIST");
        boolean manager = has(auth, "ADMIN") || has(auth, "MANAGER");
        User dispenser = users.findByUsername(user).orElseThrow();
        List<Sale.Line> lines = new ArrayList<>();
        List<StockMovement> movements = new ArrayList<>();
        boolean needsRx = false;

        for (SaleItemReq it : req.items()) {
            Medicine m = medicines.findById(it.medicineId()).filter(Medicine::isActive)
                    .orElseThrow(() -> ApiException.notFound("Medicine not found"));
            boolean restricted = RESTRICTED.contains(m.getSchedule());
            if (restricted) {
                needsRx = true;
                if (!pharmacist) throw ApiException.forbidden(m.getBrandName() + " (Schedule " + m.getSchedule()
                        + ") can only be sold by a registered pharmacist");
            }
            BigDecimal disc = it.discountPercent() == null ? BigDecimal.ZERO : it.discountPercent();
            int cap = manager ? managerMaxDiscount : maxDiscount;
            if (disc.compareTo(BigDecimal.valueOf(cap)) > 0)
                throw ApiException.bad("Discount above " + cap + "% needs manager approval");

            List<Batch> bs = sellableBatches(m.getId());
            int available = bs.stream().mapToInt(Batch::getQtyAvailable).sum();
            if (available < it.qty())
                throw ApiException.bad(m.getBrandName() + ": only " + available + " units in stock");

            int remaining = it.qty();
            for (Batch b : bs) {
                if (remaining == 0) break;
                int take = Math.min(remaining, b.getQtyAvailable());
                // atomic guard against concurrent billing of the same batch
                Batch upd = mongo.findAndModify(
                        query(where("id").is(b.getId()).and("qtyAvailable").gte(take).and("status").is("ACTIVE")),
                        new Update().inc("qtyAvailable", -take), FindAndModifyOptions.options().returnNew(true), Batch.class);
                if (upd == null) throw ApiException.conflict("Stock changed while billing " + m.getBrandName() + ". Please retry.");
                remaining -= take;
                lines.add(buildLine(m, b, take, disc, req.interState()));
                movements.add(StockMovement.builder().batchId(b.getId()).medicineId(m.getId()).type("SALE").qty(-take)
                        .refType("SALE").at(LocalDateTime.now()).by(user).build());
            }
        }
        if (needsRx && (isBlank(req.customerName()) || isBlank(req.doctorName())))
            throw ApiException.bad("Patient name and doctor name are required for prescription medicines");

        BigDecimal taxable = sum(lines, Sale.Line::getTaxable), cgst = sum(lines, Sale.Line::getCgst),
                sgst = sum(lines, Sale.Line::getSgst), igst = sum(lines, Sale.Line::getIgst), gross = sum(lines, Sale.Line::getTotal);
        BigDecimal grand = gross.setScale(0, RoundingMode.HALF_UP).setScale(2);

        Sale sale = sales.save(Sale.builder().invoiceNo(nextInvoiceNo()).invoiceDate(LocalDateTime.now(InventoryService.IST))
                .customerName(req.customerName()).customerPhone(req.customerPhone()).doctorName(req.doctorName())
                .doctorRegNo(req.doctorRegNo()).interState(req.interState()).items(lines).taxable(taxable).cgst(cgst)
                .sgst(sgst).igst(igst).roundOff(grand.subtract(gross)).grandTotal(grand).paymentMode(req.paymentMode())
                .status("COMPLETED").createdBy(user).pharmacistRegNo(pharmacist ? dispenser.getPharmacistRegNo() : null).build());

        for (StockMovement mv : movements) { mv.setRefId(sale.getId()); mongo.insert(mv); }
        for (Sale.Line l : lines) {
            String reg = "H1".equals(l.getSchedule()) ? "H1" : ("X".equals(l.getSchedule()) || "NDPS".equals(l.getSchedule())) ? "X" : null;
            if (reg != null) mongo.insert(RegisterEntry.builder().register(reg).saleId(sale.getId()).invoiceNo(sale.getInvoiceNo())
                    .medicineId(l.getMedicineId()).medicineName(l.getName()).batchNo(l.getBatchNo()).qty(l.getQty())
                    .patientName(req.customerName()).doctorName(req.doctorName()).doctorRegNo(req.doctorRegNo())
                    .dispensedBy(user).pharmacistRegNo(dispenser.getPharmacistRegNo()).at(LocalDateTime.now()).build());
        }
        audit.log("SALE", "Sale", sale.getId(), sale.getInvoiceNo() + " Rs." + grand);
        return sale;
    }

    /** MRP is GST-inclusive in India: taxable = amount / (1 + rate). Selling price never exceeds MRP. */
    private Sale.Line buildLine(Medicine m, Batch b, int qty, BigDecimal disc, boolean interState) {
        BigDecimal unitPrice = b.getMrp().divide(BigDecimal.valueOf(m.getUnitsPerPack()), 6, RoundingMode.HALF_UP);
        BigDecimal gross = unitPrice.multiply(BigDecimal.valueOf(qty));
        BigDecimal total = gross.multiply(HUNDRED.subtract(disc)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal taxable = total.multiply(HUNDRED).divide(HUNDRED.add(m.getGstPercent()), 2, RoundingMode.HALF_UP);
        BigDecimal tax = total.subtract(taxable);
        BigDecimal cgst = BigDecimal.ZERO.setScale(2), sgst = cgst, igst = cgst;
        if (interState) igst = tax;
        else { cgst = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP); sgst = tax.subtract(cgst); }
        return Sale.Line.builder().medicineId(m.getId()).batchId(b.getId()).name(m.getBrandName()).schedule(m.getSchedule())
                .batchNo(b.getBatchNo()).expiryDate(b.getExpiryDate()).hsn(m.getHsnCode()).gstPercent(m.getGstPercent())
                .mrp(b.getMrp()).unitPrice(unitPrice).qty(qty).discountPercent(disc).taxable(taxable).cgst(cgst)
                .sgst(sgst).igst(igst).total(total).build();
    }

    private static BigDecimal sum(List<Sale.Line> l, java.util.function.Function<Sale.Line, BigDecimal> f) {
        return l.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private String nextInvoiceNo() {
        LocalDate d = LocalDate.now(InventoryService.IST);
        int y = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        String fy = y + "-" + String.format("%02d", (y + 1) % 100);
        Counter c = mongo.findAndModify(query(where("id").is("invoice-" + fy)), new Update().inc("seq", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true), Counter.class);
        return String.format("INV/%s/%06d", fy, c.getSeq());
    }

    public List<Sale> recent(int limit) { return sales.findAllByOrderByInvoiceDateDesc(PageRequest.of(0, limit)); }

    public Sale get(String id) { return sales.findById(id).orElseThrow(() -> ApiException.notFound("Invoice not found")); }

    /** Cancel = status change + stock restored. Invoices and register lines are never deleted. */
    @Transactional
    public Sale cancel(String id, String reason) {
        Sale s = get(id);
        if (!"COMPLETED".equals(s.getStatus())) throw ApiException.conflict("Invoice is already cancelled");
        String user = AuditService.currentUser();
        for (Sale.Line l : s.getItems()) {
            mongo.updateFirst(query(where("id").is(l.getBatchId())), new Update().inc("qtyAvailable", l.getQty()), Batch.class);
            mongo.insert(StockMovement.builder().batchId(l.getBatchId()).medicineId(l.getMedicineId()).type("SALE_CANCEL")
                    .qty(l.getQty()).refType("SALE").refId(s.getId()).reason(reason).at(LocalDateTime.now()).by(user).build());
        }
        s.setStatus("CANCELLED"); s.setCancelReason(reason); s.setCancelledBy(user);
        sales.save(s);
        audit.log("SALE_CANCEL", "Sale", s.getId(), s.getInvoiceNo() + ": " + reason);
        return s;
    }

    public List<RegisterEntry> register(String reg, LocalDate from, LocalDate to, RegisterRepository repo) {
        return repo.findByRegisterAndAtBetweenOrderByAtAsc(reg.toUpperCase(), from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }
}
