package com.example.MedcareApp.Interafce;



import com.example.MedcareApp.Entity.Appointment;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository extends MongoRepository<Appointment, String> {
    List<Appointment> findAllByDoctorAndDateAndTime(String doctor, String date, String time);
    List<Appointment> findAllByDoctorAndDateOrderByTimeAsc(String doctor, String date);
    List<Appointment> findAllByDoctorIdAndDateOrderByTimeAsc(String doctorId, String date);
    List<Appointment> findAllByDoctorIdAndDateGreaterThanEqualOrderByDateAscTimeAsc(String doctorId, String date);
    List<Appointment> findAllByDoctorAndDateGreaterThanEqualOrderByDateAscTimeAsc(String doctor, String date);
    List<Appointment> findAllByPatientIdOrderByDateDescTimeDesc(String patientId);
}
