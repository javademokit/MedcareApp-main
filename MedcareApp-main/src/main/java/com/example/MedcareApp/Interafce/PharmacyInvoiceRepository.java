package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.pharmacy.PharmacyInvoice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PharmacyInvoiceRepository extends MongoRepository<PharmacyInvoice, String> {
    Optional<PharmacyInvoice> findByReferenceKey(String referenceKey);
    List<PharmacyInvoice> findAllByOrderByCreatedAtDesc();
    Optional<PharmacyInvoice> findByPaymentsGatewayOrderId(String gatewayOrderId);
}
