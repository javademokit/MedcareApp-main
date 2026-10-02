package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.AppointmentSlotReservation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentSlotReservationRepository
        extends MongoRepository<AppointmentSlotReservation, String> {}
