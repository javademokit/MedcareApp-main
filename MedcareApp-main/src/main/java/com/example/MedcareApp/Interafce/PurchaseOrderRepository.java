package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.pharmacy.PurchaseOrder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderRepository extends MongoRepository<PurchaseOrder, String> {
}
