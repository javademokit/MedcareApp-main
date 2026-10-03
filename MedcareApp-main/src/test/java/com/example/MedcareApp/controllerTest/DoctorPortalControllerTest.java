package com.example.MedcareApp.controllerTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Controller.DoctorPortalController;
import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.AppointmentRepository;
import com.example.MedcareApp.Interafce.ConsultationRepository;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.Interafce.MedicalTestRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.services.UserService;
import com.example.MedcareApp.services.PharmacyService;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DoctorPortalControllerTest {
    @Test
    void dashboardLoadsAppointmentsByLinkedDoctorProfileId() {
        UserService userService = org.mockito.Mockito.mock(UserService.class);
        AppointmentRepository appointmentRepository = org.mockito.Mockito.mock(AppointmentRepository.class);
        DoctorRepository doctorRepository = org.mockito.Mockito.mock(DoctorRepository.class);
        PatientRepository patientRepository = org.mockito.Mockito.mock(PatientRepository.class);
        ConsultationRepository consultationRepository = org.mockito.Mockito.mock(ConsultationRepository.class);
        MedicalTestRepository medicalTestRepository = org.mockito.Mockito.mock(MedicalTestRepository.class);
        PharmacyService pharmacyService = org.mockito.Mockito.mock(PharmacyService.class);

        user account = new user();
        account.setActive(true);
        account.setDoctorId("doctor-profile-1");
        when(userService.getUserByEmail("doctor@example.test")).thenReturn(account);
        Doctor doctor = new Doctor();
        doctor.setId("doctor-profile-1");
        doctor.setDoctorName("Dr. Example");
        when(doctorRepository.findById("doctor-profile-1")).thenReturn(Optional.of(doctor));
        Appointment appointment = new Appointment();
        appointment.setDoctorId("doctor-profile-1");
        appointment.setPatientId("PT-123");
        appointment.setDate("2026-10-03");
        appointment.setTime("09:30");
        when(appointmentRepository.findAllByDoctorIdOrderByDateDescTimeDesc("doctor-profile-1"))
                .thenReturn(List.of(appointment));
        when(appointmentRepository.findAllByDoctorOrderByDateDescTimeDesc("Dr. Example"))
                .thenReturn(List.of());

        DoctorPortalController controller = new DoctorPortalController(
                userService,
                appointmentRepository,
                doctorRepository,
                patientRepository,
                consultationRepository,
                medicalTestRepository,
                pharmacyService);
        Principal principal = () -> "doctor@example.test";

        var dashboard = controller.getDashboard(principal);

        assertEquals(List.of(appointment), dashboard.get("appointments"));
        verify(appointmentRepository).findAllByDoctorIdOrderByDateDescTimeDesc("doctor-profile-1");
    }
}
