package com.pharmacy.web;

import com.pharmacy.model.*;
import com.pharmacy.repo.Repos.*;
import com.pharmacy.service.*;
import com.pharmacy.web.Dto.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

public final class Controllers {
    private Controllers() {}

    @RestController @RequestMapping("/api/v1/auth") @RequiredArgsConstructor
    public static class AuthController {
        private final AuthService auth;
        @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }
    }

    @RestController @RequestMapping("/api/v1/medicines") @RequiredArgsConstructor
    public static class MedicineController {
        private final MedicineService svc;
        @GetMapping public List<Medicine> list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "200") int limit) {
            return svc.search(q, Math.min(limit, 500));
        }
        @GetMapping("/{id}") public Medicine get(@PathVariable String id) { return svc.get(id); }
        @PostMapping @ResponseStatus(HttpStatus.CREATED)
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PHARMACIST')")
        public Medicine create(@Valid @RequestBody MedicineRequest r) { return svc.create(r); }
        @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PHARMACIST')")
        public Medicine update(@PathVariable String id, @Valid @RequestBody MedicineRequest r) { return svc.update(id, r); }
        @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
        public void deactivate(@PathVariable String id) { svc.deactivate(id); }
    }

    @RestController @RequestMapping("/api/v1/inventory") @RequiredArgsConstructor
    public static class InventoryController {
        private final InventoryService svc;
        @GetMapping("/batches") public List<BatchView> batches(@RequestParam(required = false) String medicineId) { return svc.batches(medicineId, null); }
        @GetMapping("/expiring") public List<BatchView> expiring(@RequestParam(defaultValue = "90") int days) { return svc.batches(null, days); }
        @GetMapping("/low-stock") public List<LowStockRow> low() { return svc.lowStock(); }
        @PostMapping("/adjustments") @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PHARMACIST','STOREKEEPER')")
        public void adjust(@Valid @RequestBody AdjustRequest r) { svc.adjust(r); }
        @PatchMapping("/batches/{id}/status") @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PHARMACIST')")
        public void status(@PathVariable String id, @Valid @RequestBody StatusRequest r) { svc.setStatus(id, r.status()); }
    }

    @RestController @RequestMapping("/api/v1/suppliers") @RequiredArgsConstructor
    public static class SupplierController {
        private final SupplierRepository repo; private final AuditService audit;
        @GetMapping public List<Supplier> list() { return repo.findAll(); }
        @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STOREKEEPER')")
        public Supplier create(@Valid @RequestBody SupplierRequest r) {
            Supplier s = repo.save(Supplier.builder().name(r.name()).gstin(r.gstin()).drugLicenceNo(r.drugLicenceNo())
                    .phone(r.phone()).address(r.address()).build());
            audit.log("CREATE", "Supplier", s.getId(), s.getName());
            return s;
        }
    }

    @RestController @RequestMapping("/api/v1/purchases") @RequiredArgsConstructor
    public static class PurchaseController {
        private final PurchaseService svc; private final GrnRepository grns;
        @PostMapping("/grn") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STOREKEEPER')")
        public Grn grn(@Valid @RequestBody GrnRequest r) { return svc.createGrn(r); }
        @GetMapping @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STOREKEEPER','AUDITOR')")
        public List<Grn> list() { return grns.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)); }
    }

    @RestController @RequestMapping("/api/v1/sales") @RequiredArgsConstructor
    public static class SalesController {
        private final SalesService svc;
        @GetMapping("/lookup") public List<LookupRow> lookup(@RequestParam String q) { return svc.lookup(q); }
        @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PHARMACIST','CASHIER')")
        public Sale create(@Valid @RequestBody SaleRequest r, Authentication a) { return svc.create(r, a); }
        @GetMapping public List<Sale> recent(@RequestParam(defaultValue = "50") int limit) { return svc.recent(Math.min(limit, 200)); }
        @GetMapping("/{id}") public Sale get(@PathVariable String id) { return svc.get(id); }
        @PostMapping("/{id}/cancel") @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PHARMACIST')")
        public Sale cancel(@PathVariable String id, @Valid @RequestBody CancelRequest r) { return svc.cancel(id, r.reason()); }
    }

    @RestController @RequestMapping("/api/v1/registers") @RequiredArgsConstructor
    public static class RegisterController {
        private final SalesService svc; private final RegisterRepository repo;
        @GetMapping("/{type}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PHARMACIST','AUDITOR')")
        public List<RegisterEntry> list(@PathVariable String type,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
            return svc.register(type, from, to, repo);
        }
    }

    @RestController @RequestMapping("/api/v1/dashboard") @RequiredArgsConstructor
    public static class DashboardController {
        private final DashboardService svc;
        @GetMapping public DashboardResponse get() { return svc.summary(); }
    }

    @RestController @RequestMapping("/api/v1/audit") @RequiredArgsConstructor
    public static class AuditController {
        private final AuditRepository repo;
        @GetMapping @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
        public List<AuditLog> list(@RequestParam(defaultValue = "100") int limit) {
            return repo.findAllByOrderByAtDesc(PageRequest.of(0, Math.min(limit, 500)));
        }
    }
}
