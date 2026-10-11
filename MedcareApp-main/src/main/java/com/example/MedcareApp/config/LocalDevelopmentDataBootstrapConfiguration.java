package com.example.MedcareApp.config;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Interafce.AppointmentRepository;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalDevelopmentDataBootstrapConfiguration {

    @Bean
    ApplicationRunner seedLocalDevelopmentData(
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository) {
        return arguments -> {
            if (doctorRepository.count() == 0) {
                doctorRepository.saveAll(List.of(
                        createDoctor("DT-1001", "Dr. Meera Shah", "General Medicine", "9876543210", "Floor 1", 500.0),
                        createDoctor("DT-1002", "Dr. Arjun Nair", "Cardiology", "9876543211", "Floor 2", 800.0),
                        createDoctor("DT-1003", "Dr. Sana Khan", "Dermatology", "9876543212", "Floor 3", 650.0)
                ));
            }

            if (patientRepository.count() == 0) {
                patientRepository.saveAll(List.of(
                        createPatient("PT-1001", "Ava Patient", "30", "Female", "ava.patient@example.com",
                                "9876501234", "12 Wellness Avenue"),
                        createPatient("PT-1002", "Rohan Verma", "45", "Male", "rohan.verma@example.com",
                                "9876501235", "8 Orchard Lane"),
                        createPatient("PT-1003", "Mira Singh", "28", "Female", "mira.singh@example.com",
                                "9876501236", "21 River Park")
                ));
            }

            if (appointmentRepository.count() == 0) {
                List<Doctor> doctors = doctorRepository.findAll();
                List<Patient> patients = patientRepository.findAll();
                if (!doctors.isEmpty() && !patients.isEmpty()) {
                    Doctor doctor = doctors.get(0);
                    Patient patient = patients.get(0);
                    Appointment appointment = new Appointment();
                    appointment.setPatientId(patient.getPatientId());
                    appointment.setPatientName(patient.getPatientName());
                    appointment.setGender(patient.getGender());
                    appointment.setPatientAge(patient.getPatientAge());
                    appointment.setMobileNo(patient.getPatientmobileNo());
                    appointment.setPatientEmailId(patient.getPatientEmailId());
                    appointment.setPatientAddress(patient.getPatientAddress());
                    appointment.setDoctorId(doctor.getEmployeeId());
                    appointment.setDoctor(doctor.getDoctorName());
                    appointment.setDate(LocalDate.now().plusDays(1).toString());
                    appointment.setTime("10:00 AM");
                    appointment.setReason("General consultation");
                    appointment.setFee(String.valueOf(doctor.getDoctorfee()));
                    appointment.setAppointmentStatus("pending");
                    appointmentRepository.save(appointment);
                }
            }
        };
    }

    private Doctor createDoctor(String employeeId, String doctorName, String specialty,
                               String mobile, String destination, double fee) {
        Doctor doctor = new Doctor();
        doctor.setEmployeeId(employeeId);
        doctor.setDoctorName(doctorName);
        doctor.setDoctorSpecialistName(specialty);
        doctor.setDoctorMobileNo(mobile);
        doctor.setDoctorDestination(destination);
        doctor.setDoctorAvailabletime(List.of("09:00 AM", "10:00 AM", "11:00 AM"));
        doctor.setDoctorslot(3);
        doctor.setDoctorfee(fee);
        return doctor;
    }

    private Patient createPatient(String patientId, String name, String age, String gender,
                                 String email, String mobile, String address) {
        Patient patient = new Patient();
        patient.setPatientId(patientId);
        patient.setPatientName(name);
        patient.setPatientAge(age);
        patient.setGender(gender);
        patient.setPatientEmailId(email);
        patient.setPatientmobileNo(mobile);
        patient.setPatientAddress(address);
        return patient;
    }
}
