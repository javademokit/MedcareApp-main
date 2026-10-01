package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.pharmacy.PrescriptionIssue;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PrescriptionIssueRepository extends MongoRepository<PrescriptionIssue, String> {
}
