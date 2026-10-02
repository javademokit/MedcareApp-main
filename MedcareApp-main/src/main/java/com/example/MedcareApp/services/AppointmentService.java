package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.AppointmentSlotReservation;
import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Interafce.AppointmentRepository;
import com.example.MedcareApp.Interafce.AppointmentSlotReservationRepository;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotReservationRepository slotReservationRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    public Appointment bookAppointment(Appointment appointment) {
        validateBooking(appointment);
        Doctor doctor = StringUtils.hasText(appointment.getDoctorId())
                ? findDoctorById(appointment.getDoctorId())
                : findDoctor(appointment.getDoctor());
        if (doctor.getDoctorAvailabletime() == null || !doctor.getDoctorAvailabletime().contains(appointment.getTime())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected time is not available for this doctor");
        }
        boolean slotTaken = appointmentRepository
                .findAllByDoctorAndDateAndTime(doctor.getDoctorName(), appointment.getDate(), appointment.getTime())
                .stream()
                .filter(existing -> !StringUtils.hasText(existing.getDoctorId())
                        || doctor.getId().equals(existing.getDoctorId()))
                .anyMatch(existing -> !"cancelled".equalsIgnoreCase(existing.getAppointmentStatus()));
        if (slotTaken) throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected appointment slot is already booked");

        appointment.setDoctorId(doctor.getId());
        appointment.setDoctor(doctor.getDoctorName());
        appointment.setFee(String.valueOf(doctor.getDoctorfee()));
        String reservationId = slotReservationId(doctor.getId(), appointment.getDate(), appointment.getTime());
        try {
            slotReservationRepository.insert(new AppointmentSlotReservation(
                    reservationId, appointment.getId(), java.time.Instant.now()));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Selected appointment slot is already booked", exception);
        }

        try {
            Patient patient = StringUtils.hasText(appointment.getPatientId())
                    ? findPatient(appointment.getPatientId())
                    : createPatient(appointment);
            appointment.setPatientId(patient.getPatientId());
            appointment.setPatientName(patient.getPatientName());
            appointment.setGender(patient.getGender());
            appointment.setPatientAge(patient.getPatientAge());
            appointment.setMobileNo(patient.getPatientmobileNo());
            appointment.setPatientEmailId(patient.getPatientEmailId());
            appointment.setPatientAddress(patient.getPatientAddress());
            appointment.setAppointmentStatus("pending");
            patient.setPatientAppointmentdate(appointment.getDate());
            patientRepository.save(patient);
            return appointmentRepository.save(appointment);
        } catch (RuntimeException exception) {
            try {
                slotReservationRepository.deleteById(reservationId);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public Appointment bookForPatient(String patientId, Appointment appointment) {
        if (appointment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment details are required");
        }
        appointment.setPatientId(patientId);
        return bookAppointment(appointment);
    }

    public List<Appointment> getAppointmentsForPatient(String patientId) {
        return appointmentRepository.findAllByPatientIdOrderByDateDescTimeDesc(patientId);
    }

    public List<String> getAvailableTimes(String doctorId, String date) {
        if (!StringUtils.hasText(doctorId) || !StringUtils.hasText(date)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doctor and date are required");
        }
        LocalDate appointmentDate;
        try {
            appointmentDate = LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment date must use YYYY-MM-DD");
        }
        if (appointmentDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment date cannot be in the past");
        }

        Doctor doctor = findDoctorById(doctorId);
        List<String> schedule = doctor.getDoctorAvailabletime();
        if (schedule == null || schedule.isEmpty()) return List.of();

        Set<String> bookedTimes = new java.util.HashSet<>();
        appointmentRepository.findAllByDoctorIdAndDateOrderByTimeAsc(doctor.getId(), date).stream()
                .filter(appointment -> !"cancelled".equalsIgnoreCase(appointment.getAppointmentStatus()))
                .map(Appointment::getTime)
                .filter(StringUtils::hasText)
                .forEach(bookedTimes::add);
        List<Doctor> sameNameDoctors = doctorRepository.findAllByDoctorName(doctor.getDoctorName());
        if (sameNameDoctors.size() == 1) {
            appointmentRepository.findAllByDoctorAndDateOrderByTimeAsc(doctor.getDoctorName(), date).stream()
                    .filter(appointment -> !StringUtils.hasText(appointment.getDoctorId()))
                    .filter(appointment -> !"cancelled".equalsIgnoreCase(appointment.getAppointmentStatus()))
                    .map(Appointment::getTime)
                    .filter(StringUtils::hasText)
                    .forEach(bookedTimes::add);
        }

        return schedule.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(time -> !bookedTimes.contains(time))
                .filter(time -> !slotReservationRepository.existsById(slotReservationId(doctor.getId(), date, time)))
                .distinct()
                .toList();
    }

    public Appointment updateStatus(String id, String status) {
        if (!StringUtils.hasText(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment status is required");
        }
        String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("pending", "confirmed", "cancelled", "completed").contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment status is invalid");
        }
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        appointment.setAppointmentStatus(normalizedStatus);
        Appointment updated = appointmentRepository.save(appointment);
        if ("cancelled".equals(normalizedStatus)) {
            slotReservationRepository.deleteById(
                    slotReservationId(
                            StringUtils.hasText(appointment.getDoctorId())
                                    ? appointment.getDoctorId()
                                    : appointment.getDoctor(),
                            appointment.getDate(),
                            appointment.getTime()));
        }
        return updated;
    }

    static String slotReservationId(String doctor, String date, String time) {
        String normalized = String.join("|",
                doctor.trim().toLowerCase(Locale.ROOT),
                date.trim(),
                time.trim().toLowerCase(Locale.ROOT));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Patient createPatient(Appointment appointment) {
        String mobileNo = appointment.getMobileNo().trim();
        boolean duplicate = patientRepository.findAllByPatientmobileNo(mobileNo).stream()
                .anyMatch(patient -> patient.getPatientName() != null
                        && patient.getPatientName().trim().equalsIgnoreCase(appointment.getPatientName().trim()));
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A patient with this name and mobile number already exists. Select their existing Patient ID.");
        }
        Patient patient = new Patient();
        patient.setPatientId("PT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        patient.setPatientName(appointment.getPatientName().trim());
        patient.setPatientAge(appointment.getPatientAge().trim());
        patient.setGender(appointment.getGender().trim());
        patient.setPatientEmailId(appointment.getPatientEmailId() == null ? null : appointment.getPatientEmailId().trim());
        patient.setPatientmobileNo(mobileNo);
        patient.setPatientAddress(appointment.getPatientAddress() == null ? null : appointment.getPatientAddress().trim());
        patient.setPatientAppointmentdate(appointment.getDate());
        return patient;
    }

    private Patient findPatient(String patientId) {
        List<Patient> matches = patientRepository.findAllByPatientId(patientId);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient ID not found");
        }
        if (matches.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient ID is not unique");
        }
        return matches.get(0);
    }

    private Doctor findDoctorById(String doctorId) {
        return doctorRepository.findById(doctorId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected doctor was not found"));
    }

    private Doctor findDoctor(String doctorName) {
        List<Doctor> matches = doctorRepository.findAllByDoctorName(doctorName);
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected doctor was not found");
        if (matches.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Doctor name is not unique");
        return matches.get(0);
    }

    private void validateBooking(Appointment appointment) {
        if (appointment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment details are required");
        }
        if (!StringUtils.hasText(appointment.getDoctorId()) && !StringUtils.hasText(appointment.getDoctor())
                || !StringUtils.hasText(appointment.getDate())
                || !StringUtils.hasText(appointment.getTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doctor, date, and time are required");
        }
        try {
            LocalDate appointmentDate = LocalDate.parse(appointment.getDate());
            if (appointmentDate.isBefore(LocalDate.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment date cannot be in the past");
            }
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment date must use YYYY-MM-DD");
        }
        if (!StringUtils.hasText(appointment.getPatientId())
                && (!StringUtils.hasText(appointment.getPatientName())
                || !StringUtils.hasText(appointment.getPatientAge())
                || !StringUtils.hasText(appointment.getGender())
                || !StringUtils.hasText(appointment.getMobileNo()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Patient name, age, gender, and mobile number are required for a new patient");
        }
        if (!StringUtils.hasText(appointment.getPatientId())) {
            if (!Set.of("male", "female", "other").contains(appointment.getGender().trim().toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid patient gender");
            }
            try {
                int age = Integer.parseInt(appointment.getPatientAge());
                if (age < 0 || age > 120) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient age must be a whole number from 0 to 120");
            }
        }
    }
}
