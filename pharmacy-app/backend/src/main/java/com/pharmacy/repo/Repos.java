package com.pharmacy.repo;

import com.pharmacy.model.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public final class Repos {
    private Repos() {}

    public interface UserRepository extends MongoRepository<User, String> {
        Optional<User> findByUsername(String username);
    }
    public interface MedicineRepository extends MongoRepository<Medicine, String> {}
    public interface BatchRepository extends MongoRepository<Batch, String> {
        Optional<Batch> findByMedicineIdAndBatchNo(String medicineId, String batchNo);
    }
    public interface SupplierRepository extends MongoRepository<Supplier, String> {}
    public interface GrnRepository extends MongoRepository<Grn, String> {
        boolean existsBySupplierIdAndSupplierInvoiceNo(String supplierId, String invoiceNo);
        List<Grn> findAllByOrderByCreatedAtDesc(Pageable p);
    }
    public interface SaleRepository extends MongoRepository<Sale, String> {
        List<Sale> findAllByOrderByInvoiceDateDesc(Pageable p);
    }
    public interface RegisterRepository extends MongoRepository<RegisterEntry, String> {
        List<RegisterEntry> findByRegisterAndAtBetweenOrderByAtAsc(String register, LocalDateTime from, LocalDateTime to);
    }
    public interface AuditRepository extends MongoRepository<AuditLog, String> {
        List<AuditLog> findAllByOrderByAtDesc(Pageable p);
    }
}
