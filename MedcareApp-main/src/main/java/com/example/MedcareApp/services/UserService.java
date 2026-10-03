package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.web.DoctorProfileRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "DOCTOR", "NURSE", "HEAD_NURSE",
            "RECEPTIONIST", "CRM_EXECUTIVE", "BILLING_EXECUTIVE", "FINANCE", "HR", "PHARMACIST",
            "LAB_TECHNICIAN", "PATIENT");

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public user createUser(user newUser) {
        if (newUser == null || !StringUtils.hasText(newUser.getEmailId())
                || !StringUtils.hasText(newUser.getUserId())
                || newUser.getPassword() == null || newUser.getPassword().length() < 12) {
            throw new IllegalArgumentException("Enter a user name, valid email, and a password of at least 12 characters");
        }
        String normalizedEmail = newUser.getEmailId().trim().toLowerCase(Locale.ROOT);
        if (!userRepository.findAllByEmailIdIgnoreCase(normalizedEmail).isEmpty()) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        newUser.setEmailId(normalizedEmail);
        newUser.setRoles(Set.of("PATIENT"));
        newUser.setActive(true);
        newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
        user savedUser = userRepository.save(newUser);
        ensurePatientProfileForUser(savedUser);
        return savedUser;
    }

    public user createStaffUser(user newUser, Set<String> roles) {
        return createStaffUser(newUser, roles, null);
    }

    public user createStaffUser(user newUser, Set<String> roles, DoctorProfileRequest doctorProfile) {
        if (newUser == null || !StringUtils.hasText(newUser.getEmailId())
                || !StringUtils.hasText(newUser.getUserId())
                || newUser.getPassword() == null || newUser.getPassword().length() < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a user name, valid email, and a password of at least 12 characters");
        }

        String normalizedEmail = newUser.getEmailId().trim().toLowerCase(Locale.ROOT);
        String normalizedUserId = newUser.getUserId().trim();
        Set<String> normalizedRoles = roles == null ? Set.of() : roles.stream()
                .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> staffRoles = new java.util.HashSet<>(ALLOWED_ROLES);
        staffRoles.remove("PATIENT");

        if (normalizedRoles.isEmpty() || !staffRoles.containsAll(normalizedRoles)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select one or more valid staff roles");
        }
        if (normalizedRoles.contains("DOCTOR")) {
            if (doctorProfile != null) {
                if (StringUtils.hasText(newUser.getDoctorId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Choose either a new doctor profile or an existing profile");
                }
            } else {
                validateDoctorProfile(newUser.getDoctorId());
                ensureDoctorProfileUnlinked(newUser.getDoctorId(), null);
            }
        } else {
            if (doctorProfile != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A doctor profile can only be created for a doctor account");
            }
            newUser.setDoctorId(null);
        }
        if (!userRepository.findAllByEmailIdIgnoreCase(normalizedEmail).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        if (!userRepository.findByUserId(normalizedUserId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this user ID already exists");
        }

        newUser.setUserId(normalizedUserId);
        newUser.setEmailId(normalizedEmail);
        newUser.setRoles(normalizedRoles);
        newUser.setActive(true);
        newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
        if (normalizedRoles.contains("DOCTOR") && doctorProfile != null) {
            Doctor doctor = new Doctor();
            doctor.setDoctorName(doctorProfile.getDoctorName().trim());
            doctor.setDoctorSpecialistName(doctorProfile.getDoctorSpecialistName().trim());
            doctor.setDoctorMobileNo(StringUtils.trimWhitespace(doctorProfile.getDoctorMobileNo()));
            doctor.setDoctorDestination(StringUtils.trimWhitespace(doctorProfile.getDoctorDestination()));
            List<String> availableTimes = doctorProfile.getDoctorAvailabletime().stream()
                    .map(String::trim)
                    .distinct()
                    .toList();
            doctor.setDoctorAvailabletime(availableTimes);
            doctor.setDoctorslot(availableTimes.size());
            doctor.setDoctorfee(doctorProfile.getDoctorfee());
            doctor.setEmployeeId(StaffIdentifierGenerator.generate("DT"));
            Doctor savedDoctor = doctorRepository.save(doctor);
            newUser.setDoctorId(savedDoctor.getId());
        }
        if (normalizedRoles.contains("PHARMACIST")) {
            newUser.setEmployeeCode(StaffIdentifierGenerator.generate("PT"));
        }
        return userRepository.save(newUser);
    }

    public user assignDoctorProfile(String userId, String doctorId) {
        List<user> matches = userRepository.findByUserId(userId);
        if (matches.size() != 1) {
            throw new ResponseStatusException(matches.isEmpty() ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                    matches.isEmpty() ? "User not found" : "User ID is not unique");
        }
        user account = matches.get(0);
        if (!account.getRoles().contains("DOCTOR")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Assign the DOCTOR role before linking a doctor profile");
        }
        validateDoctorProfile(doctorId);
        ensureDoctorProfileUnlinked(doctorId, account.getId());
        account.setDoctorId(doctorId.trim());
        return userRepository.save(account);
    }

    private void ensureDoctorProfileUnlinked(String doctorId, String exceptUserId) {
        boolean doctorAlreadyLinked = userRepository.findAll().stream()
                .anyMatch(other -> !java.util.Objects.equals(other.getId(), exceptUserId)
                        && other.getDoctorId() != null
                        && other.getDoctorId().equals(doctorId.trim()));
        if (doctorAlreadyLinked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This doctor profile is already linked to another login account");
        }
    }

    private Doctor validateDoctorProfile(String doctorId) {
        if (!StringUtils.hasText(doctorId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Select a doctor profile for this doctor account");
        }
        return doctorRepository.findById(doctorId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Selected doctor profile was not found"));
    }

    public user authenticate(String emailId, String rawPassword) {
        if (!StringUtils.hasText(emailId) || rawPassword == null) return null;
        List<user> matchingUsers = userRepository.findAllByEmailIdIgnoreCase(
                emailId.trim().toLowerCase(Locale.ROOT));
        if (matchingUsers.size() != 1) return null;
        user account = matchingUsers.get(0);
        if (!account.isActive() || !StringUtils.hasText(account.getPassword())) return null;
        String storedPassword = account.getPassword();
        if (passwordEncoder.matches(rawPassword, storedPassword)) return account;

        if (!storedPassword.startsWith("$2") && storedPassword.equals(rawPassword)) {
            account.setPassword(passwordEncoder.encode(rawPassword));
            return userRepository.save(account);
        }
        return null;
    }

    public List<user> getAllUsers() {
        return userRepository.findAll().stream().map(account -> {
            if (account.getRoles().contains("PHARMACIST")
                    && (account.getEmployeeCode() == null || !account.getEmployeeCode().startsWith("PT-"))) {
                account.setEmployeeCode(StaffIdentifierGenerator.generate("PT"));
                return userRepository.save(account);
            }
            return account;
        }).toList();
    }

    public List<user> findByUserId(String userId) {
        return userRepository.findByUserId(userId);
    }

    public user updateUser(String userId, user updates) {
        List<user> existingUsers = userRepository.findByUserId(userId);
        if (existingUsers.size() != 1) {
            throw new ResponseStatusException(existingUsers.isEmpty() ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                    existingUsers.isEmpty() ? "User not found" : "User ID is not unique");
        }
        user existingUser = existingUsers.get(0);
        if (StringUtils.hasText(updates.getEmailId())) {
            String email = updates.getEmailId().trim().toLowerCase(Locale.ROOT);
            boolean emailUsedByAnotherAccount = userRepository.findAllByEmailIdIgnoreCase(email).stream()
                    .anyMatch(account -> !account.getId().equals(existingUser.getId()));
            if (emailUsedByAnotherAccount) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
            existingUser.setEmailId(email);
        }
        if (StringUtils.hasText(updates.getMobileNo())) existingUser.setMobileNo(updates.getMobileNo().trim());
        if (StringUtils.hasText(updates.getPassword())) {
            if (updates.getPassword().length() < 12) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 12 characters");
            }
            existingUser.setPassword(passwordEncoder.encode(updates.getPassword()));
        }
        return userRepository.save(existingUser);
    }

    public user updateRoles(String userId, Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one role is required");
        }
        Set<String> normalizedRoles = roles.stream()
                .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!ALLOWED_ROLES.containsAll(normalizedRoles)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more roles are invalid");
        }
        List<user> matches = userRepository.findByUserId(userId);
        if (matches.size() != 1) {
            throw new ResponseStatusException(matches.isEmpty() ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                    matches.isEmpty() ? "User not found" : "User ID is not unique");
        }
        user account = matches.get(0);
        account.setRoles(normalizedRoles);
        return userRepository.save(account);
    }

    public user setActive(String userId, boolean active) {
        List<user> matches = userRepository.findByUserId(userId);
        if (matches.size() != 1) {
            throw new ResponseStatusException(matches.isEmpty() ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                    matches.isEmpty() ? "User not found" : "User ID is not unique");
        }
        user account = matches.get(0);
        account.setActive(active);
        return userRepository.save(account);
    }

    public user getUserByEmail(String emailId) {
        List<user> matchingUsers = userRepository.findAllByEmailIdIgnoreCase(emailId);
        return matchingUsers.size() == 1 ? matchingUsers.get(0) : null;
    }

    public Patient ensurePatientProfileForUser(user account) {
        if (account == null || !StringUtils.hasText(account.getEmailId())) {
            throw new IllegalArgumentException("A valid account email is required to create a patient profile");
        }

        String normalizedEmail = account.getEmailId().trim().toLowerCase(Locale.ROOT);
        List<Patient> matches = patientRepository.findAllByPatientEmailIdIgnoreCase(normalizedEmail);
        if (matches.size() > 1) {
            throw new IllegalStateException("Multiple patient profiles use this email. Contact reception to resolve the records.");
        }
        if (!matches.isEmpty()) {
            return matches.get(0);
        }

        Patient patient = new Patient();
        patient.setPatientId("PT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        patient.setPatientName(StringUtils.hasText(account.getUserId()) ? account.getUserId().trim() : "Patient");
        patient.setPatientEmailId(normalizedEmail);
        patient.setPatientmobileNo(account.getMobileNo() == null ? null : account.getMobileNo().trim());
        patient.setPatientAddress(null);
        patient.setPatientAge(null);
        patient.setGender(null);
        patient.setPatientAppointmentdate(null);
        return patientRepository.save(patient);
    }

    public user promoteExistingAccountToSuperAdmin(String emailId) {
        if (!StringUtils.hasText(emailId)) {
            throw new IllegalArgumentException("An exact account email is required for administrator promotion");
        }
        List<user> matchingUsers = userRepository.findAllByEmailIdIgnoreCase(
                emailId.trim().toLowerCase(Locale.ROOT));
        if (matchingUsers.size() != 1) {
            throw new IllegalStateException(matchingUsers.isEmpty()
                    ? "No account found for the configured bootstrap administrator email"
                    : "Multiple accounts match the configured bootstrap administrator email");
        }
        user account = matchingUsers.get(0);
        Set<String> roles = new java.util.HashSet<>(account.getRoles());
        roles.add("SUPER_ADMIN");
        account.setRoles(roles);
        return userRepository.save(account);
    }

    public void deleteUser(String userId) {
        List<user> matches = userRepository.findByUserId(userId);
        if (matches.size() != 1) {
            throw new ResponseStatusException(matches.isEmpty() ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                    matches.isEmpty() ? "User not found" : "User ID is not unique");
        }
        userRepository.delete(matches.get(0));
    }
}
