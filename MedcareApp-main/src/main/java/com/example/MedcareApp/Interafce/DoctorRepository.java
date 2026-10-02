package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.Doctor;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DoctorRepository extends MongoRepository<Doctor, String> {
    List<Doctor> findAllByDoctorName(String doctorName);
}