package com.example.payroll.repo;

import com.example.payroll.model.PayslipSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface PayslipSnapshotRepository extends MongoRepository<PayslipSnapshot, Long> {
    List<PayslipSnapshot> findByRunId(Long runId);
}
