package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.emergency.AmbulanceBooking;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AmbulanceBookingRepository extends MongoRepository<AmbulanceBooking, String> {
    List<AmbulanceBooking> findAllByOrderByCreatedAtDesc();
    Optional<AmbulanceBooking> findByPaymentsGatewayOrderId(String gatewayOrderId);
}
