package com.example.payroll.repo;

import com.example.payroll.model.Employee;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface EmployeeRepository extends MongoRepository<Employee, Long> {
    List<Employee> findAllByOrderByIdAsc();
}
