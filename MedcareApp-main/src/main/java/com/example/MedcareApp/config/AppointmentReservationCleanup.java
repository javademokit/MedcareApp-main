package com.example.MedcareApp.config;

import com.example.MedcareApp.Interafce.AppointmentRepository;
import com.example.MedcareApp.Interafce.AppointmentSlotReservationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AppointmentReservationCleanup {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentReservationCleanup.class);

    private final AppointmentSlotReservationRepository reservations;
    private final AppointmentRepository appointments;

    @Bean
    ApplicationRunner removeOrphanedAppointmentReservations() {
        return arguments -> {
            int removed = 0;
            var existingReservations = reservations.findAll();
            for (var reservation : existingReservations) {
                var appointment = appointments.findById(reservation.getAppointmentId());
                if (appointment.isEmpty()
                        || "cancelled".equalsIgnoreCase(appointment.get().getAppointmentStatus())) {
                    reservations.delete(reservation);
                    removed++;
                }
            }
            if (removed > 0) {
                LOGGER.info("Removed {} orphaned appointment slot reservations", removed);
            }
        };
    }
}
