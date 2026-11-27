package com.medora.assistant.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<AppointmentEntity, Long> {

    List<AppointmentEntity> findByPatientNameAndStatusNot(String patientName, String status);

    List<AppointmentEntity> findByDoctorNameAndAppointmentDateAndStatusNot(String doctorName, LocalDate date, String status);

    @Query("SELECT a FROM AppointmentEntity a WHERE a.patientName = :patientName AND a.status != 'cancelled' ORDER BY a.appointmentDate ASC, a.appointmentTime ASC")
    List<AppointmentEntity> findActiveByPatientName(@Param("patientName") String patientName);

    @Query("SELECT a FROM AppointmentEntity a WHERE a.status = 'scheduled' ORDER BY a.appointmentDate ASC, a.appointmentTime ASC")
    List<AppointmentEntity> findAllScheduled();

    @Query("SELECT a FROM AppointmentEntity a WHERE a.appointmentDate = :date AND a.status != 'cancelled' ORDER BY a.appointmentTime ASC")
    List<AppointmentEntity> findByDate(@Param("date") LocalDate date);
}
