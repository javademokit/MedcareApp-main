package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.Ward;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WardRepository extends MongoRepository<Ward, String> {}
