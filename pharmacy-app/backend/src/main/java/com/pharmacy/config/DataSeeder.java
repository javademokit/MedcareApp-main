package com.pharmacy.config;

import com.pharmacy.model.*;
import com.pharmacy.repo.Repos.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates demo users, medicines and stock on first start. Remove / change passwords before real use. */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final MedicineRepository medicines;
    private final BatchRepository batches;
    private final SupplierRepository suppliers;
    private final PasswordEncoder enc;

    @Override
    public void run(String... args) {
        if (users.count() == 0) {
            user("admin", "Admin User", "Admin@12345", Set.of("ADMIN"), null);
            user("manager", "Store Manager", "Manager@12345", Set.of("MANAGER"), null);
            user("pharmacist", "Demo Pharmacist", "Pharma@12345", Set.of("PHARMACIST"), "KA-12345");
            user("cashier", "Counter Cashier", "Cashier@12345", Set.of("CASHIER"), null);
        }
        if (medicines.count() == 0) {
            Supplier s = suppliers.save(Supplier.builder().name("Demo Pharma Distributors").gstin("29ABCDE1234F1Z5")
                    .drugLicenceNo("KA-WS-0001").phone("9999999999").address("Bengaluru").build());
            LocalDate now = LocalDate.now();
            seed(s, "Dolo 650", "Paracetamol 650mg", "Micro Labs", "Tablet", 15, "30049099", "12", "OTC", 60, "D650A1", now.plusMonths(14), "33.00");
            seed(s, "Dolo 650", "Paracetamol 650mg", "Micro Labs", null, 15, null, null, null, 0, "D650B2", now.plusMonths(5), "33.00");
            seed(s, "Azithral 500", "Azithromycin 500mg", "Alembic", "Tablet", 5, "30042019", "12", "H", 30, "AZ500K", now.plusMonths(10), "118.00");
            seed(s, "Alprax 0.25", "Alprazolam 0.25mg", "Torrent", "Tablet", 10, "30049099", "12", "H1", 20, "ALP25Z", now.plusMonths(8), "42.00");
            seed(s, "Crocin Advance", "Paracetamol 500mg", "GSK", "Tablet", 15, "30049099", "12", "OTC", 40, "CRA500", now.plusDays(45), "20.00");
        }
    }

    private void user(String u, String name, String pw, Set<String> roles, String reg) {
        users.save(User.builder().username(u).fullName(name).passwordHash(enc.encode(pw)).roles(roles).pharmacistRegNo(reg).active(true).build());
    }

    private void seed(Supplier s, String brand, String generic, String mfr, String form, int units, String hsn, String gst,
                      String schedule, int reorder, String batchNo, LocalDate expiry, String mrp) {
        Medicine m = medicines.findAll().stream().filter(x -> x.getBrandName().equals(brand)).findFirst().orElse(null);
        if (m == null) m = medicines.save(Medicine.builder().brandName(brand).genericName(generic).manufacturer(mfr).dosageForm(form)
                .unitsPerPack(units).hsnCode(hsn).gstPercent(new BigDecimal(gst)).schedule(schedule).storage("ROOM")
                .reorderLevel(reorder).active(true).build());
        batches.save(Batch.builder().medicineId(m.getId()).batchNo(batchNo).mfgDate(LocalDate.now().minusMonths(4)).expiryDate(expiry)
                .purchaseRate(new BigDecimal(mrp).multiply(new BigDecimal("0.75"))).mrp(new BigDecimal(mrp))
                .qtyAvailable(units * 20).supplierId(s.getId()).status("ACTIVE").build());
    }
}
