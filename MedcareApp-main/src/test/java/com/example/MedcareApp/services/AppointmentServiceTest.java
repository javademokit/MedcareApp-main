package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.AppointmentSlotReservation;
import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.billing.AppointmentInvoice;
import com.example.MedcareApp.Interafce.AppointmentRepository;
import com.example.MedcareApp.Interafce.AppointmentSlotReservationRepository;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.services.AppointmentBillingService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private AppointmentSlotReservationRepository slotReservationRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private AppointmentBillingService billingService;
    @InjectMocks private AppointmentService service;

    @Test
    void bookingNewPatientCreatesOneRecordAndReturnsItsCanonicalId() {
        prepareDoctorAndSlot();
        when(patientRepository.findAll()).thenReturn(List.of());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        prepareInvoice();
        Appointment request = appointment();

        Appointment booked = service.bookAppointment(request);

        assertTrue(booked.getPatientId().matches("PT-[A-F0-9]{32}"));
        assertEquals(booked.getPatientId(), request.getPatientId());
        assertEquals("doctor-profile-1", booked.getDoctorId());
        assertEquals("pending", booked.getAppointmentStatus());
        verify(patientRepository).save(any(Patient.class));
        verify(appointmentRepository).save(request);
    }

    @Test
    void bookingExistingPatientUsesStoredDemographicsAndId() {
        prepareDoctorAndSlot();
        Patient patient = new Patient();
        patient.setPatientId("PT-EXISTING");
        patient.setPatientName("Existing Patient");
        patient.setPatientAge("37");
        patient.setGender("Female");
        patient.setPatientmobileNo("5551234");
        when(patientRepository.findAllByPatientId("PT-EXISTING")).thenReturn(List.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        prepareInvoice();
        Appointment request = appointment();
        request.setPatientId("PT-UNTRUSTED");
        request.setPatientName("Untrusted edited name");
        request.setMobileNo("0000000");

        Appointment booked = service.bookForPatient("PT-EXISTING", request);

        assertEquals("PT-EXISTING", booked.getPatientId());
        assertEquals("Existing Patient", booked.getPatientName());
        assertEquals("5551234", booked.getMobileNo());
    }

    @Test
    void bookingAgainByMobileKeepsPatientIdWhenDoctorChanges() {
        Patient patient = new Patient();
        patient.setPatientId("PT-EXISTING");
        patient.setPatientName("A Patient");
        patient.setPatientAge("37");
        patient.setGender("Female");
        patient.setPatientmobileNo("(555) 1234");
        Doctor anotherDoctor = new Doctor();
        anotherDoctor.setId("doctor-profile-2");
        anotherDoctor.setDoctorName("Dr. Other");
        anotherDoctor.setDoctorAvailabletime(List.of("10:00 AM"));
        anotherDoctor.setDoctorfee(700);
        when(doctorRepository.findById("doctor-profile-2")).thenReturn(Optional.of(anotherDoctor));
        when(appointmentRepository.findAllByDoctorAndDateAndTime(
                "Dr. Other", appointment().getDate(), "10:00 AM")).thenReturn(List.of());
        when(patientRepository.findAll()).thenReturn(List.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        prepareInvoice();
        Appointment request = appointment();
        request.setDoctorId("doctor-profile-2");
        request.setDoctor("Dr. Other");

        Appointment booked = service.bookAppointment(request);

        assertEquals("PT-EXISTING", booked.getPatientId());
        assertEquals("Dr. Other", booked.getDoctor());
        assertEquals("doctor-profile-2", booked.getDoctorId());
        verify(patientRepository).save(patient);
    }

    @Test
    void bookingNewPatientCanReuseMobileNumberWhenExplicitlyRequested() {
        prepareDoctorAndSlot();
        Patient existingPatient = new Patient();
        existingPatient.setPatientId("PT-EXISTING");
        existingPatient.setPatientName("Existing Patient");
        existingPatient.setPatientmobileNo("5551234");
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        prepareInvoice();
        Appointment request = appointment();
        request.setPatientName("Shyam");
        request.setRegisterNewPatient(true);

        Appointment booked = service.bookAppointment(request);

        assertEquals("Shyam", booked.getPatientName());
        assertNotEquals("PT-EXISTING", booked.getPatientId());
        assertEquals("5551234", booked.getMobileNo());
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void bookingCannotDoubleBookAnActiveDoctorSlot() {
        Appointment existing = new Appointment();
        existing.setAppointmentStatus("confirmed");
        Doctor doctor = new Doctor();
        doctor.setDoctorName("Dr. Example");
        doctor.setDoctorAvailabletime(List.of("10:00 AM"));
        doctor.setId("doctor-profile-1");
        when(doctorRepository.findById("doctor-profile-1")).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findAllByDoctorAndDateAndTime(
                "Dr. Example", appointment().getDate(), "10:00 AM")).thenReturn(List.of(existing));

        assertThrows(ResponseStatusException.class, () -> service.bookAppointment(appointment()));
    }

    @Test
    void availableTimesExcludeBookedSlotsAndKeepCancelledSlots() {
        String date = LocalDate.now().plusDays(1).toString();
        Doctor doctor = new Doctor();
        doctor.setId("doctor-profile-1");
        doctor.setDoctorName("Dr. Example");
        doctor.setDoctorAvailabletime(List.of("10:00 AM", "10:30 AM"));
        when(doctorRepository.findById("doctor-profile-1")).thenReturn(Optional.of(doctor));
        when(doctorRepository.findAllByDoctorName("Dr. Example")).thenReturn(List.of(doctor));
        Appointment booked = new Appointment();
        booked.setTime("10:00 AM");
        booked.setAppointmentStatus("confirmed");
        Appointment cancelled = new Appointment();
        cancelled.setTime("10:30 AM");
        cancelled.setAppointmentStatus("cancelled");
        when(appointmentRepository.findAllByDoctorIdAndDateOrderByTimeAsc("doctor-profile-1", date))
                .thenReturn(List.of(booked, cancelled));

        assertEquals(List.of("10:30 AM"), service.getAvailableTimes("doctor-profile-1", date));
    }

    @Test
    void concurrentSlotReservationConflictPreventsSecondBooking() {
        prepareDoctorAndSlot();
        when(slotReservationRepository.insert(any(AppointmentSlotReservation.class)))
                .thenThrow(new DuplicateKeyException("occupied"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.bookAppointment(appointment()));

        assertEquals(409, exception.getStatusCode().value());
        verify(appointmentRepository, org.mockito.Mockito.never()).save(any(Appointment.class));
    }

    @Test
    void cancellingAppointmentReleasesItsSlotReservation() {
        Appointment appointment = appointment();
        appointment.setId("appointment-1");
        appointment.setPatientId("PT-EXISTING");
        when(appointmentRepository.findById("appointment-1")).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        service.updateStatus("appointment-1", "cancelled");

        verify(slotReservationRepository).deleteById(any(String.class));
        verify(billingService).cancelInvoice("appointment-1");
    }

    @Test
    void unpaidInvoicePreventsAppointmentConfirmation() {
        Appointment appointment = appointment();
        appointment.setId("appointment-1");
        AppointmentInvoice invoice = new AppointmentInvoice();
        invoice.setAmount(new java.math.BigDecimal("500.00"));
        invoice.setPaidAmount(java.math.BigDecimal.ZERO);
        invoice.setStatus("PENDING");
        when(appointmentRepository.findById("appointment-1")).thenReturn(Optional.of(appointment));
        when(billingService.getInvoiceForAppointment("appointment-1")).thenReturn(invoice);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.updateStatus("appointment-1", "confirmed"));

        assertEquals(409, exception.getStatusCode().value());
        verify(appointmentRepository, org.mockito.Mockito.never()).save(any(Appointment.class));
    }

    @Test
    void paidAndNoChargeInvoicesAllowAppointmentConfirmation() {
        for (String billingStatus : List.of("PAID", "NO_CHARGE")) {
            Appointment appointment = appointment();
            appointment.setId("appointment-" + billingStatus);
            AppointmentInvoice invoice = new AppointmentInvoice();
            invoice.setStatus(billingStatus);
            invoice.setAmount("PAID".equals(billingStatus)
                    ? new java.math.BigDecimal("500.00") : java.math.BigDecimal.ZERO);
            invoice.setPaidAmount("PAID".equals(billingStatus)
                    ? new java.math.BigDecimal("500.00") : java.math.BigDecimal.ZERO);
            when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
            when(billingService.getInvoiceForAppointment(appointment.getId())).thenReturn(invoice);
            when(appointmentRepository.save(appointment)).thenReturn(appointment);

            assertEquals("confirmed", service.updateStatus(appointment.getId(), "confirmed").getAppointmentStatus());
        }
    }

    @Test
    void bookingCreatesInvoiceAtServerFeeAndReturnsInvoiceSummary() {
        prepareDoctorAndSlot();
        when(patientRepository.findAll()).thenReturn(List.of());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        prepareInvoice();

        Appointment booked = service.bookAppointment(appointment());

        assertEquals("500.00", booked.getFee());
        assertEquals("invoice-1", booked.getInvoiceId());
        assertEquals("PENDING", booked.getBillingStatus());
        assertEquals("500.00", booked.getBalanceDue());
        verify(billingService).createInvoice(booked);
    }

    @Test
    void bookingWithinFortyEightHoursGetsNoChargeInvoice() {
        prepareDoctorAndSlot();
        Patient patient = new Patient();
        patient.setPatientId("PT-EXISTING");
        patient.setPatientName("Existing Patient");
        patient.setPatientAge("37");
        patient.setGender("Female");
        patient.setPatientmobileNo("5551234");
        when(patientRepository.findAllByPatientId("PT-EXISTING")).thenReturn(List.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Appointment previous = new Appointment();
        LocalDateTime previousDateTime = LocalDateTime.now().minusHours(47);
        previous.setDate(previousDateTime.toLocalDate().toString());
        previous.setTime(previousDateTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)));
        previous.setAppointmentStatus("completed");
        when(appointmentRepository.findAllByPatientIdOrderByDateDescTimeDesc("PT-EXISTING"))
                .thenReturn(List.of(previous));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AppointmentInvoice noChargeInvoice = new AppointmentInvoice();
        noChargeInvoice.setId("invoice-follow-up");
        noChargeInvoice.setStatus("NO_CHARGE");
        noChargeInvoice.setAmount(java.math.BigDecimal.ZERO.setScale(2));
        noChargeInvoice.setPaidAmount(java.math.BigDecimal.ZERO.setScale(2));
        when(billingService.createInvoice(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment booked = invocation.getArgument(0);
            assertEquals("0.00", booked.getFee());
            return noChargeInvoice;
        });

        Appointment request = appointment();
        request.setPatientId("PT-EXISTING");

        Appointment booked = service.bookAppointment(request);

        assertEquals("NO_CHARGE", booked.getBillingStatus());
        assertEquals("0.00", booked.getBalanceDue());
    }

    @Test
    void bookingAfterFortyEightHoursChargesTheConsultationFee() {
        prepareDoctorAndSlot();
        Patient patient = new Patient();
        patient.setPatientId("PT-EXISTING");
        patient.setPatientName("Existing Patient");
        patient.setPatientAge("37");
        patient.setGender("Female");
        patient.setPatientmobileNo("5551234");
        when(patientRepository.findAllByPatientId("PT-EXISTING")).thenReturn(List.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findAllByPatientIdOrderByDateDescTimeDesc("PT-EXISTING"))
                .thenReturn(List.of(appointmentHoursAgo(49)));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        prepareInvoice();
        Appointment request = appointment();
        request.setPatientId("PT-EXISTING");

        Appointment booked = service.bookAppointment(request);

        assertEquals("500.00", booked.getFee());
    }

    @Test
    void recentAppointmentForAnotherPatientWithSameMobileDoesNotApplyFollowUpFee() {
        prepareDoctorAndSlot();
        Patient patient = new Patient();
        patient.setPatientId("PT-NEW-PATIENT");
        patient.setPatientName("New Patient");
        patient.setPatientAge("37");
        patient.setGender("Female");
        patient.setPatientmobileNo("5551234");
        when(patientRepository.findAllByPatientId("PT-NEW-PATIENT")).thenReturn(List.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findAllByPatientIdOrderByDateDescTimeDesc("PT-NEW-PATIENT"))
                .thenReturn(List.of());
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        prepareInvoice();
        Appointment request = appointment();
        request.setPatientId("PT-NEW-PATIENT");

        Appointment booked = service.bookAppointment(request);

        assertEquals("500.00", booked.getFee());
        verify(appointmentRepository, never()).findAllByMobileNo("5551234");
    }

    private Appointment appointment() {
        Appointment appointment = new Appointment();
        appointment.setPatientName("A Patient");
        appointment.setPatientAge("37");
        appointment.setGender("Female");
        appointment.setMobileNo("5551234");
        appointment.setDoctorId("doctor-profile-1");
        appointment.setDoctor("Dr. Example");
        appointment.setDate(LocalDate.now().plusDays(1).toString());
        appointment.setTime("10:00 AM");
        return appointment;
    }

    private Appointment appointmentHoursAgo(long hoursAgo) {
        LocalDateTime dateTime = LocalDateTime.now().minusHours(hoursAgo);
        Appointment appointment = new Appointment();
        appointment.setPatientId("PT-EXISTING");
        appointment.setDate(dateTime.toLocalDate().toString());
        appointment.setTime(dateTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)));
        appointment.setAppointmentStatus("completed");
        return appointment;
    }

    private void prepareDoctorAndSlot() {
        Doctor doctor = new Doctor();
        doctor.setDoctorName("Dr. Example");
        doctor.setId("doctor-profile-1");
        doctor.setDoctorfee(500);
        doctor.setDoctorAvailabletime(List.of("10:00 AM"));
        when(doctorRepository.findById("doctor-profile-1")).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findAllByDoctorAndDateAndTime(any(), any(), any())).thenReturn(List.of());
    }

    private void prepareInvoice() {
        AppointmentInvoice invoice = new AppointmentInvoice();
        invoice.setId("invoice-1");
        invoice.setStatus("PENDING");
        invoice.setAmount(new java.math.BigDecimal("500.00"));
        invoice.setPaidAmount(java.math.BigDecimal.ZERO.setScale(2));
        when(billingService.createInvoice(any(Appointment.class))).thenReturn(invoice);
    }
}
