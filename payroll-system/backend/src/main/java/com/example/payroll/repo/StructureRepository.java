package com.example.payroll.repo;

import com.example.payroll.model.Structure;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StructureRepository extends MongoRepository<Structure, Long> {}
