package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.billing.AppointmentInvoice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface AppointmentInvoiceRepository extends MongoRepository<AppointmentInvoice, String> {
    List<AppointmentInvoice> findAllByOrderByCreatedAtDesc();
    Optional<AppointmentInvoice> findByAppointmentId(String appointmentId);
    @Query("{'payments.gatewayOrderId': ?0}")
    Optional<AppointmentInvoice> findByPaymentsGatewayOrderId(String gatewayOrderId);
}
