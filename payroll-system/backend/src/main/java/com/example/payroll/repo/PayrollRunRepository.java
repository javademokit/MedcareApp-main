package com.example.payroll.repo;

import com.example.payroll.model.PayrollRun;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface PayrollRunRepository extends MongoRepository<PayrollRun, Long> {
    Optional<PayrollRun> findByPayMonth(String payMonth);
    List<PayrollRun> findAllByOrderByPayMonthDesc();
}
