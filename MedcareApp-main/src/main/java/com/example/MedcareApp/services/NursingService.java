package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.Consultation;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.nursing.NurseHandover;
import com.example.MedcareApp.Entity.nursing.NurseProfile;
import com.example.MedcareApp.Entity.nursing.NurseShiftRoster;
import com.example.MedcareApp.Entity.nursing.NurseShiftSwap;
import com.example.MedcareApp.Entity.nursing.NursingCareRecord;
import com.example.MedcareApp.Entity.nursing.PatientAssignment;
import com.example.MedcareApp.Entity.nursing.Ward;
import com.example.MedcareApp.Entity.nursing.WardBed;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.ConsultationRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.Interafce.nursing.NurseHandoverRepository;
import com.example.MedcareApp.Interafce.nursing.NurseProfileRepository;
import com.example.MedcareApp.Interafce.nursing.NurseShiftRosterRepository;
import com.example.MedcareApp.Interafce.nursing.NurseShiftSwapRepository;
import com.example.MedcareApp.Interafce.nursing.NursingCareRecordRepository;
import com.example.MedcareApp.Interafce.nursing.PatientAssignmentRepository;
import com.example.MedcareApp.Interafce.nursing.WardBedRepository;
import com.example.MedcareApp.Interafce.nursing.WardRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NursingService {
    private static final Set<String> SHIFTS = Set.of("MORNING", "EVENING", "NIGHT");
    private static final Set<String> NURSE_STATUSES = Set.of("ACTIVE", "ON_LEAVE", "INACTIVE");
    private static final Set<String> DESIGNATIONS = Set.of("STAFF_NURSE", "SENIOR_NURSE", "HEAD_NURSE");
    private static final Set<String> ASSIGNMENT_ROLES = Set.of("PRIMARY", "BACKUP");
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final WardRepository wardRepository;
    private final WardBedRepository wardBedRepository;
    private final NurseShiftRosterRepository rosterRepository;
    private final NurseShiftSwapRepository shiftSwapRepository;
    private final PatientAssignmentRepository assignmentRepository;
    private final NurseHandoverRepository handoverRepository;
    private final NursingCareRecordRepository careRecordRepository;
    private final ConsultationRepository consultationRepository;

    public List<NurseProfile> getNurseProfiles() {
        return nurseProfileRepository.findAll();
    }

    public List<NurseProfile> getNurseProfilesForManager(String accountId) {
        Set<String> wardScope = managerWardScope(accountId);
        if (wardScope == null) return getNurseProfiles();
        Set<String> nurseIds = rosterRepository.findAll().stream()
                .filter(roster -> "SCHEDULED".equals(roster.getStatus()))
                .filter(roster -> wardScope.contains(roster.getWardId()))
                .filter(roster -> !LocalDate.parse(roster.getEndDate()).isBefore(LocalDate.now()))
                .map(NurseShiftRoster::getNurseId)
                .collect(java.util.stream.Collectors.toSet());
        nurseIds.add(accountId);
        return nurseProfileRepository.findAll().stream()
                .filter(profile -> nurseIds.contains(profile.getAccountId()))
                .toList();
    }

    public NurseProfile saveNurseProfile(String accountId, NurseProfile profile, String actor) {
        requireProfileScope(accountId, actor);
        user account = getNurseAccount(accountId);
        if (profile == null || !StringUtils.hasText(profile.getName())
                || !StringUtils.hasText(profile.getEmployeeId())
                || !StringUtils.hasText(profile.getLicenseNumber())
                || !StringUtils.hasText(profile.getDesignation())
                || !StringUtils.hasText(profile.getSpecialization())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name, employee ID, license number, designation, and specialization are required");
        }
        String designation = normalize(profile.getDesignation());
        if (!DESIGNATIONS.contains(designation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid nurse designation");
        }
        boolean duplicateProfile = nurseProfileRepository.findAll().stream()
                .filter(existing -> !account.getId().equals(existing.getAccountId()))
                .anyMatch(existing -> profile.getEmployeeId().trim().equalsIgnoreCase(existing.getEmployeeId())
                        || profile.getLicenseNumber().trim().equalsIgnoreCase(existing.getLicenseNumber()));
        if (duplicateProfile) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Employee ID and license number must be unique across nurse profiles");
        }
        String status = StringUtils.hasText(profile.getStatus()) ? normalize(profile.getStatus()) : "ACTIVE";
        if (!NURSE_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select Active, On leave, or Inactive status");
        }
        NurseProfile saved = nurseProfileRepository.findByAccountId(account.getId()).orElseGet(NurseProfile::new);
        saved.setAccountId(account.getId());
        saved.setName(profile.getName().trim());
        saved.setPhotoUrl(trimToNull(profile.getPhotoUrl()));
        saved.setPhone(trimToNull(profile.getPhone()));
        saved.setEmail(account.getEmailId());
        saved.setEmployeeId(profile.getEmployeeId().trim());
        saved.setQualification(trimToNull(profile.getQualification()));
        saved.setLicenseNumber(profile.getLicenseNumber().trim());
        saved.setDesignation(designation);
        saved.setSpecialization(profile.getSpecialization().trim());
        saved.setStatus(status);
        NurseProfile updated = nurseProfileRepository.save(saved);
        if (!"ACTIVE".equals(status)) {
            unassignUnavailableNurse(account.getId(), actor);
        }
        return updated;
    }

    public List<Ward> getWards() {
        return wardRepository.findAll();
    }

    public Ward saveWard(Ward ward) {
        if (ward == null || !StringUtils.hasText(ward.getName()) || !StringUtils.hasText(ward.getType())
                || ward.getMaxPatientsPerNurse() < 1 || ward.getMinimumNursesPerShift() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ward name, type, and a positive nurse-to-patient capacity are required");
        }
        ward.setName(ward.getName().trim());
        ward.setType(ward.getType().trim().toUpperCase(Locale.ROOT));
        return wardRepository.save(ward);
    }

    public List<WardBed> getBeds(String wardId, String accountId) {
        requireWardScope(accountId, wardId);
        requireWard(wardId);
        return wardBedRepository.findByWardId(wardId);
    }

    public WardBed addBed(String wardId, WardBed bed, String actor) {
        requireActorWardScope(actor, wardId);
        requireWard(wardId);
        if (bed == null || !StringUtils.hasText(bed.getBedNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bed number is required");
        }
        if (wardBedRepository.findByWardIdAndBedNumber(wardId, bed.getBedNumber().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That bed number already exists in this ward");
        }
        bed.setId(null);
        bed.setWardId(wardId);
        bed.setBedNumber(bed.getBedNumber().trim());
        bed.setStatus("VACANT");
        bed.setPatientId(null);
        return wardBedRepository.save(bed);
    }

    public WardBed updateBedStatus(String wardId, String bedId, String status, String actor) {
        requireActorWardScope(actor, wardId);
        WardBed bed = wardBedRepository.findById(bedId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bed was not found"));
        if (!wardId.equals(bed.getWardId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bed was not found in this ward");
        }
        String normalizedStatus = normalize(status);
        if (!Set.of("VACANT", "RESERVED").contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bed status can only be changed to VACANT or RESERVED");
        }
        if ("OCCUPIED".equals(bed.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discharge the patient before changing an occupied bed");
        }
        bed.setStatus(normalizedStatus);
        return wardBedRepository.save(bed);
    }
    public List<NurseShiftRoster> getRosters(String accountId) {
        Set<String> wardScope = managerWardScope(accountId);
        return rosterRepository.findAll().stream()
                .filter(roster -> wardScope == null || wardScope.contains(roster.getWardId()))
                .toList();
    }

    public List<NurseShiftRoster> getNurseRosters(String accountId) {
        getNurseAccount(accountId);
        LocalDate today = LocalDate.now();
        return rosterRepository.findByNurseIdAndStatus(accountId, "SCHEDULED").stream()
                .filter(roster -> !LocalDate.parse(roster.getEndDate()).isBefore(today))
                .toList();
    }

    public NurseShiftRoster saveRoster(NurseShiftRoster roster, String actor) {
        if (roster == null || !StringUtils.hasText(roster.getNurseId())
                || !StringUtils.hasText(roster.getWardId()) || !StringUtils.hasText(roster.getShift())
                || !StringUtils.hasText(roster.getStartDate()) || !StringUtils.hasText(roster.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nurse, ward, shift, and date range are required for a roster entry");
        }
        LocalDate start = parseDate(roster.getStartDate(), "Roster start date");
        LocalDate end = parseDate(roster.getEndDate(), "Roster end date");
        if (end.isBefore(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Roster end date must not be before start date");
        roster.setShift(validateShift(roster.getShift()));
        roster.setId(null);
        requireActorWardScope(actor, roster.getWardId());
        NurseProfile nurse = getNurseProfile(roster.getNurseId());
        if (!"ACTIVE".equals(nurse.getStatus()) || !isActiveNurseAccount(nurse.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active nurses can be scheduled");
        }
        requireWard(roster.getWardId());
        boolean overlap = rosterRepository.findByNurseIdAndStatus(nurse.getAccountId(), "SCHEDULED").stream()
                .filter(existing -> existing.getShift().equals(roster.getShift()))
                .filter(existing -> roster.getId() == null || !existing.getId().equals(roster.getId()))
                .anyMatch(existing -> !end.isBefore(LocalDate.parse(existing.getStartDate()))
                        && !start.isAfter(LocalDate.parse(existing.getEndDate())));
        if (overlap) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This nurse already has a roster entry for an overlapping date and shift");
        }
        roster.setNurseId(nurse.getAccountId());
        roster.setStartDate(start.toString());
        roster.setEndDate(end.toString());
        roster.setStatus("SCHEDULED");
        roster.setCreatedBy(actor);
        return rosterRepository.save(roster);
    }

    public List<NurseShiftSwap> getShiftSwaps(String accountId, boolean manager) {
        if (manager) {
            Set<String> wardScope = managerWardScope(accountId);
            return shiftSwapRepository.findAllByOrderByRequestedAtDesc().stream()
                    .filter(swap -> wardScope == null || wardScope.contains(swap.getWardId()))
                    .toList();
        }
        getNurseAccount(accountId);
        return shiftSwapRepository.findAllByOrderByRequestedAtDesc().stream()
                .filter(swap -> accountId.equals(swap.getFromNurseId()) || accountId.equals(swap.getToNurseId()))
                .toList();
    }

    public NurseShiftSwap requestShiftSwap(
            String fromNurseId, String toNurseId, String wardId, String shift, String date, String note) {
        if (!StringUtils.hasText(fromNurseId) || !StringUtils.hasText(toNurseId)
                || !StringUtils.hasText(wardId) || !StringUtils.hasText(shift)
                || !StringUtils.hasText(date)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nurse, ward, shift, and date are required for a shift swap");
        }
        if (fromNurseId.equals(toNurseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose another nurse for the shift swap");
        }
        String normalizedShift = validateShift(shift);
        LocalDate shiftDate = parseDate(date, "Shift date");
        if (shiftDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A shift swap cannot be requested for a past date");
        }
        Ward ward = requireWard(wardId);
        NurseProfile fromNurse = getNurseProfile(fromNurseId);
        NurseProfile toNurse = getNurseProfile(toNurseId);
        requireOnDuty(fromNurse, ward, normalizedShift, shiftDate);
        requireOnDuty(toNurse, ward, normalizedShift, shiftDate);
        boolean duplicate = shiftSwapRepository.findAllByOrderByRequestedAtDesc().stream()
                .anyMatch(existing -> "PENDING".equals(existing.getStatus())
                        && fromNurseId.equals(existing.getFromNurseId())
                        && toNurseId.equals(existing.getToNurseId())
                        && wardId.equals(existing.getWardId())
                        && normalizedShift.equals(existing.getShift())
                        && shiftDate.toString().equals(existing.getDate()));
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending shift swap already exists for this nurse, ward, shift, and date");
        }
        NurseShiftSwap swap = new NurseShiftSwap();
        swap.setFromNurseId(fromNurseId);
        swap.setToNurseId(toNurseId);
        swap.setWardId(wardId);
        swap.setShift(normalizedShift);
        swap.setDate(shiftDate.toString());
        swap.setNote(trimToNull(note));
        swap.setRequestedBy(fromNurseId);
        return shiftSwapRepository.save(swap);
    }

    public NurseShiftSwap decideShiftSwap(String swapId, boolean approve, String reviewer, String note) {
        NurseShiftSwap swap = shiftSwapRepository.findById(swapId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift swap request was not found"));
        requireActorWardScope(reviewer, swap.getWardId());
        if (!"PENDING".equals(swap.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This shift swap request has already been reviewed");
        }
        if (approve) {
            LocalDate date = parseDate(swap.getDate(), "Shift date");
            NurseShiftRoster first = requireRoster(swap.getFromNurseId(), swap.getWardId(), swap.getShift(), date);
            NurseShiftRoster second = requireRoster(swap.getToNurseId(), swap.getWardId(), swap.getShift(), date);
            replaceRosterForDate(first, swap.getToNurseId(), date);
            replaceRosterForDate(second, swap.getFromNurseId(), date);
            swap.setStatus("APPROVED");
        } else {
            swap.setStatus("DECLINED");
        }
        swap.setReviewedBy(reviewer);
        swap.setReviewedAt(Instant.now());
        swap.setDecisionNote(trimToNull(note));
        return shiftSwapRepository.save(swap);
    }

    private NurseShiftRoster requireRoster(String nurseId, String wardId, String shift, LocalDate date) {
        return rosterRepository.findByNurseIdAndStatus(nurseId, "SCHEDULED").stream()
                .filter(roster -> wardId.equals(roster.getWardId()) && shift.equals(roster.getShift()))
                .filter(roster -> covers(roster, date))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Both nurses must still be scheduled for this ward, shift, and date"));
    }

    private void replaceRosterForDate(NurseShiftRoster roster, String replacementNurseId, LocalDate date) {
        LocalDate start = LocalDate.parse(roster.getStartDate());
        LocalDate end = LocalDate.parse(roster.getEndDate());
        roster.setStatus("CANCELLED");
        rosterRepository.save(roster);
        if (start.isBefore(date)) saveRosterSegment(roster, roster.getNurseId(), start, date.minusDays(1));
        saveRosterSegment(roster, replacementNurseId, date, date);
        if (end.isAfter(date)) saveRosterSegment(roster, roster.getNurseId(), date.plusDays(1), end);
    }

    private void saveRosterSegment(NurseShiftRoster source, String nurseId, LocalDate start, LocalDate end) {
        NurseShiftRoster segment = new NurseShiftRoster();
        segment.setNurseId(nurseId);
        segment.setWardId(source.getWardId());
        segment.setShift(source.getShift());
        segment.setStartDate(start.toString());
        segment.setEndDate(end.toString());
        segment.setStatus("SCHEDULED");
        segment.setCreatedBy(source.getCreatedBy());
        rosterRepository.save(segment);
    }

    public List<Map<String, Object>> getWardOccupancy(String accountId) {
        List<Patient> admitted = patientRepository.findAll().stream().filter(this::isAdmitted).toList();
        Set<String> wardScope = managerWardScope(accountId);
        return wardRepository.findAll().stream()
                .filter(ward -> wardScope == null || wardScope.contains(ward.getId()))
                .map(ward -> {
            List<WardBed> beds = wardBedRepository.findByWardId(ward.getId());
            List<Map<String, Object>> allBeds = beds.stream().map(bed -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", bed.getId());
                item.put("bedNumber", bed.getBedNumber());
                item.put("status", bed.getStatus());
                item.put("patientId", bed.getPatientId() == null ? "" : bed.getPatientId());
                return item;
                    }).toList();
            List<Map<String, Object>> occupiedBeds = admitted.stream()
                    .filter(patient -> ward.getId().equals(patient.getPatientWardId()))
                    .map(patient -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("bedId", patient.getPatientBedId() == null ? "" : patient.getPatientBedId());
                        row.put("bedNumber", patient.getPatientBedId() == null ? "Not assigned"
                                : wardBedRepository.findById(patient.getPatientBedId()).map(WardBed::getBedNumber).orElse("Not recorded"));
                        row.put("patientId", patient.getPatientId());
                        row.put("patientName", patient.getPatientName());
                        row.put("assignedNurse", patient.getPatientNurseassign() == null ? "Not assigned" : patient.getPatientNurseassign());
                        row.put("occupied", true);
                        return row;
                    }).toList();
            Map<String, Object> item = new HashMap<>();
            item.put("id", ward.getId());
            item.put("name", ward.getName());
            item.put("type", ward.getType());
            item.put("maxPatientsPerNurse", ward.getMaxPatientsPerNurse());
            item.put("minimumNursesPerShift", ward.getMinimumNursesPerShift());
            item.put("totalBeds", beds.size());
            item.put("vacantBeds", beds.stream().filter(bed -> "VACANT".equals(bed.getStatus())).count());
            item.put("beds", allBeds);
            item.put("patients", occupiedBeds);
            List<String> nursesOnDuty = rosterRepository.findByWardIdAndStatus(ward.getId(), "SCHEDULED").stream()
                    .filter(roster -> currentShift().equals(roster.getShift()))
                    .filter(roster -> covers(roster, LocalDate.now()))
                    .map(NurseShiftRoster::getNurseId)
                    .distinct()
                    .filter(nurseAccountId -> nurseProfileRepository.findByAccountId(nurseAccountId)
                            .filter(profile -> "ACTIVE".equals(profile.getStatus()))
                            .filter(profile -> isActiveNurseAccount(profile.getAccountId())).isPresent())
                    .toList();
            item.put("nursesOnDuty", nursesOnDuty.size());
            item.put("understaffed", nursesOnDuty.size() < ward.getMinimumNursesPerShift());
            return item;
        }).toList();
    }

    public Patient admitPatient(String patientId, String wardId, String bedId, String actor) {
        Patient patient = findPatient(patientId);
        if (isAdmitted(patient)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient is already admitted");
        Ward ward = requireWard(wardId);
        requireActorWardScope(actor, wardId);
        WardBed bed = wardBedRepository.findById(bedId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected bed was not found"));
        if (!ward.getId().equals(bed.getWardId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected bed does not belong to this ward");
        }
        if (!"VACANT".equals(bed.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected bed is not vacant");
        }
        patient.setPatientAdmitdate(LocalDate.now().toString());
        patient.setPatientDischargedate(null);
        patient.setPatientWardnum(ward.getName());
        patient.setPatientWardId(ward.getId());
        patient.setPatientBedId(bed.getId());
        bed.setStatus("OCCUPIED");
        bed.setPatientId(patientId);
        wardBedRepository.save(bed);
        patientRepository.save(patient);
        autoAssignPatient(patient, actor);
        return patientRepository.save(patient);
    }

    public Patient transferPatient(String patientId, String wardId, String bedId, String actor) {
        Patient patient = findPatient(patientId);
        if (!isAdmitted(patient)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only admitted patients can be transferred");
        }
        requireActorWardScope(actor, patient.getPatientWardId());
        requireActorWardScope(actor, wardId);
        Ward targetWard = requireWard(wardId);
        WardBed targetBed = wardBedRepository.findById(bedId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected bed was not found"));
        if (!wardId.equals(targetBed.getWardId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected bed does not belong to this ward");
        }
        if (bedId.equals(patient.getPatientBedId())) return patient;
        if (!"VACANT".equals(targetBed.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected bed is not vacant");
        }
        if (patient.getPatientBedId() != null) {
            wardBedRepository.findById(patient.getPatientBedId()).ifPresent(previousBed -> {
                if (patientId.equals(previousBed.getPatientId())) {
                    previousBed.setStatus("VACANT");
                    previousBed.setPatientId(null);
                    wardBedRepository.save(previousBed);
                }
            });
        }
        assignmentRepository.findByPatientIdAndStatus(patientId, "ACTIVE").forEach(this::closeAssignment);
        targetBed.setStatus("OCCUPIED");
        targetBed.setPatientId(patientId);
        wardBedRepository.save(targetBed);
        patient.setPatientWardId(targetWard.getId());
        patient.setPatientWardnum(targetWard.getName());
        patient.setPatientBedId(targetBed.getId());
        patient.setPatientNurseId(null);
        patient.setPatientNurseassign(null);
        patientRepository.save(patient);
        autoAssignPatient(patient, actor);
        return patientRepository.save(patient);
    }

    public void dischargePatient(Patient patient) {
        if (patient.getPatientBedId() != null) {
            wardBedRepository.findById(patient.getPatientBedId()).ifPresent(bed -> {
                if (patient.getPatientId().equals(bed.getPatientId())) {
                    bed.setStatus("VACANT");
                    bed.setPatientId(null);
                    wardBedRepository.save(bed);
                }
            });
        }
        assignmentRepository.findByPatientIdAndStatus(patient.getPatientId(), "ACTIVE").forEach(this::closeAssignment);
        patient.setPatientNurseId(null);
        patient.setPatientNurseassign(null);
        patient.setPatientWardId(null);
        patient.setPatientBedId(null);
    }

    public PatientAssignment assignPatient(String patientId, String nurseId, String role, String shift, String actor) {
        Patient patient = findPatient(patientId);
        if (!isAdmitted(patient)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Nurses can only be assigned to currently admitted patients");
        Ward ward = findPatientWard(patient);
        requireActorWardScope(actor, ward.getId());
        NurseProfile nurse = getNurseProfile(nurseId);
        String normalizedRole = StringUtils.hasText(role) ? normalize(role) : "PRIMARY";
        if (!ASSIGNMENT_ROLES.contains(normalizedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment role must be PRIMARY or BACKUP");
        }
        String normalizedShift = StringUtils.hasText(shift) ? validateShift(shift) : currentShift();
        requireOnDuty(nurse, ward, normalizedShift, LocalDate.now());
        if ("PRIMARY".equals(normalizedRole)) {
            enforceWorkload(nurse, ward, patientId);
            assignmentRepository.findByPatientIdAndStatus(patientId, "ACTIVE").stream()
                    .filter(existing -> "PRIMARY".equals(existing.getRole()))
                    .forEach(this::closeAssignment);
            patient.setPatientNurseId(nurse.getAccountId());
            patient.setPatientNurseassign(nurse.getName());
            patientRepository.save(patient);
        }
        PatientAssignment assignment = new PatientAssignment();
        assignment.setPatientId(patientId);
        assignment.setNurseId(nurse.getAccountId());
        assignment.setWardId(ward.getId());
        assignment.setBedId(patient.getPatientBedId());
        assignment.setShift(normalizedShift);
        assignment.setRole(normalizedRole);
        assignment.setAssignedBy(actor);
        assignment.setSource("MANUAL");
        return assignmentRepository.save(assignment);
    }

    public PatientAssignment autoAssignPatient(String patientId, String actor) {
        Patient patient = findPatient(patientId);
        if (!isAdmitted(patient)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only admitted patients can be assigned");
        requireActorWardScope(actor, findPatientWard(patient).getId());
        autoAssignPatient(patient, actor);
        return assignmentRepository.findByPatientIdAndStatus(patientId, "ACTIVE").stream()
                .filter(assignment -> "PRIMARY".equals(assignment.getRole()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No on-duty nurse in this ward has capacity under the nurse-to-patient ratio"));
    }

    public List<PatientAssignment> assignByWard(
            String wardId, String bedFrom, String bedTo, String nurseId, String role, String shift, String actor) {
        Ward ward = requireWard(wardId);
        requireActorWardScope(actor, wardId);
        NurseProfile nurse = getNurseProfile(nurseId);
        String normalizedRole = StringUtils.hasText(role) ? normalize(role) : "PRIMARY";
        String normalizedShift = StringUtils.hasText(shift) ? validateShift(shift) : currentShift();
        int firstBed = StringUtils.hasText(bedFrom) ? parseBedNumber(bedFrom) : Integer.MIN_VALUE;
        int lastBed = StringUtils.hasText(bedTo) ? parseBedNumber(bedTo) : Integer.MAX_VALUE;
        if (lastBed < firstBed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bed range end must not be before its start");
        }
        if (!ASSIGNMENT_ROLES.contains(normalizedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment role must be PRIMARY or BACKUP");
        }
        requireOnDuty(nurse, ward, normalizedShift, LocalDate.now());
        List<Patient> selectedPatients = patientRepository.findAll().stream()
                .filter(this::isAdmitted)
                .filter(patient -> wardId.equals(patient.getPatientWardId()))
                .filter(patient -> inBedRange(patient.getPatientBedId(), bedFrom, bedTo))
                .toList();
        if ("PRIMARY".equals(normalizedRole)) {
            long newAssignments = selectedPatients.stream()
                    .filter(patient -> assignmentRepository.findByPatientIdAndStatus(patient.getPatientId(), "ACTIVE").stream()
                            .noneMatch(existing -> nurseId.equals(existing.getNurseId())
                                    && "PRIMARY".equals(existing.getRole())))
                    .count();
            if (workload(nurseId, wardId, "") + newAssignments > ward.getMaxPatientsPerNurse()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        nurse.getName() + " would exceed the ward nurse-to-patient ratio");
            }
        }
        return selectedPatients.stream()
                .map(patient -> assignPatient(patient.getPatientId(), nurseId, normalizedRole, normalizedShift, actor))
                .toList();
    }

    public List<PatientAssignment> getAssignments(String accountId) {
        Set<String> wardScope = managerWardScope(accountId);
        return assignmentRepository.findAll().stream()
                .filter(assignment -> wardScope == null || wardScope.contains(assignment.getWardId()))
                .toList();
    }

    public List<Patient> getUnassignedPatients(String accountId) {
        Set<String> wardScope = managerWardScope(accountId);
        return patientRepository.findAll().stream()
                .filter(this::isAdmitted)
                .filter(patient -> wardScope == null || wardScope.contains(patient.getPatientWardId()))
                .filter(patient -> assignmentRepository.findByPatientIdAndStatus(patient.getPatientId(), "ACTIVE").stream()
                        .noneMatch(assignment -> "PRIMARY".equals(assignment.getRole())))
                .toList();
    }

    public List<Map<String, Object>> getPatientAssignmentBoard(String accountId) {
        Set<String> wardScope = managerWardScope(accountId);
        return patientRepository.findAll().stream()
                .filter(this::isAdmitted)
                .filter(patient -> wardScope == null || wardScope.contains(patient.getPatientWardId()))
                .map(patient -> {
                    List<PatientAssignment> active = assignmentRepository.findByPatientIdAndStatus(
                            patient.getPatientId(), "ACTIVE");
                    Map<String, Object> row = new HashMap<>();
                    row.put("patient", patient);
                    row.put("primary", active.stream().filter(item -> "PRIMARY".equals(item.getRole())).findFirst().orElse(null));
                    row.put("backup", active.stream().filter(item -> "BACKUP".equals(item.getRole())).findFirst().orElse(null));
                    return row;
                }).toList();
    }

    public List<NursingCareRecord> getCareRecords(String accountId) {
        Set<String> wardScope = managerWardScope(accountId);
        return careRecordRepository.findAllByOrderByRecordedAtDesc().stream()
                .filter(record -> wardScope == null || isPatientInWards(record.getPatientId(), wardScope))
                .toList();
    }

    public List<Map<String, Object>> getNurseDashboard(String accountId) {
        getNurseAccount(accountId);
        return assignmentRepository.findByNurseIdAndStatus(accountId, "ACTIVE").stream()
                .map(assignment -> {
                    Patient patient = findPatient(assignment.getPatientId());
                    Map<String, Object> row = new HashMap<>();
                    row.put("assignment", assignment);
                    row.put("patient", patient);
                    row.put("bed", patient.getPatientBedId() == null ? null
                            : wardBedRepository.findById(patient.getPatientBedId()).orElse(null));
                    row.put("ward", patient.getPatientWardId() == null ? null
                            : wardRepository.findById(patient.getPatientWardId()).orElse(null));
                    row.put("latestConsultation", consultationRepository
                            .findAllByPatientIdOrderByCreatedAtDesc(patient.getPatientId()).stream().findFirst().orElse(null));
                    row.put("careRecords", careRecordRepository.findByPatientIdOrderByRecordedAtDesc(patient.getPatientId()));
                    return row;
                }).toList();
    }

    public NursingCareRecord addCareRecord(String accountId, String patientId, NursingCareRecord record) {
        requireActiveAssignment(accountId, patientId);
        if (record == null || !StringUtils.hasText(record.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Care record type is required");
        }
        String type = normalize(record.getType());
        if (!Set.of("VITALS", "MEDICATION", "TASK", "NOTE").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Care record type must be VITALS, MEDICATION, TASK, or NOTE");
        }
        record.setId(null);
        record.setPatientId(patientId);
        record.setNurseId(accountId);
        record.setType(type);
        record.setStatus(Set.of("MEDICATION", "TASK").contains(type) ? "PENDING" : "RECORDED");
        record.setRecordedAt(Instant.now());
        return careRecordRepository.save(record);
    }

    public NursingCareRecord completeCareRecord(String accountId, String patientId, String recordId, NursingCareRecord update) {
        requireActiveAssignment(accountId, patientId);
        NursingCareRecord record = careRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nursing task was not found"));
        if (!patientId.equals(record.getPatientId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nursing task was not found for this patient");
        if (!"PENDING".equals(record.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "This nursing task is already completed");
        record.setStatus("COMPLETED");
        record.setNurseId(accountId);
        record.setNote(update == null ? null : trimToNull(update.getNote()));
        record.setRecordedAt(Instant.now());
        return careRecordRepository.save(record);
    }

    public List<NurseHandover> getPendingHandovers(String accountId) {
        getNurseAccount(accountId);
        return handoverRepository.findByToNurseIdAndStatusOrderByCreatedAtDesc(accountId, "PENDING");
    }

    public NurseHandover createHandover(
            String accountId, String patientId, String incomingNurseId, String shift, String note) {
        PatientAssignment current = requireActiveAssignment(accountId, patientId);
        NurseProfile incoming = getNurseProfile(incomingNurseId);
        if (!StringUtils.hasText(note)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Handover note is required");
        String incomingShift = StringUtils.hasText(shift) ? validateShift(shift) : currentShift();
        requireOnDuty(incoming, requireWard(current.getWardId()), incomingShift, LocalDate.now());
        enforceWorkload(incoming, requireWard(current.getWardId()), patientId);
        NurseHandover handover = new NurseHandover();
        handover.setPatientId(patientId);
        handover.setFromNurseId(accountId);
        handover.setToNurseId(incoming.getAccountId());
        handover.setShift(incomingShift);
        handover.setNote(note.trim());
        handover.setCreatedBy(accountId);
        return handoverRepository.save(handover);
    }

    public NurseHandover acknowledgeHandover(String accountId, String handoverId) {
        NurseHandover handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Handover was not found"));
        if (!accountId.equals(handover.getToNurseId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This handover belongs to another nurse");
        if (!"PENDING".equals(handover.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "This handover is no longer pending");
        Patient patient = findPatient(handover.getPatientId());
        String incomingShift = StringUtils.hasText(handover.getShift()) ? handover.getShift() : currentShift();
        requireOnDuty(getNurseProfile(accountId), requireWard(patient.getPatientWardId()), incomingShift, LocalDate.now());
        List<PatientAssignment> active = assignmentRepository.findByPatientIdAndStatus(patient.getPatientId(), "ACTIVE");
        PatientAssignment previous = active.stream()
                .filter(assignment -> handover.getFromNurseId().equals(assignment.getNurseId())
                        && "PRIMARY".equals(assignment.getRole()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Outgoing nurse assignment is no longer active"));
        closeAssignment(previous);
        PatientAssignment next = new PatientAssignment();
        next.setPatientId(patient.getPatientId());
        next.setNurseId(accountId);
        next.setWardId(previous.getWardId());
        next.setBedId(previous.getBedId());
        next.setShift(incomingShift);
        next.setRole("PRIMARY");
        next.setAssignedBy(accountId);
        next.setSource("HANDOVER");
        assignmentRepository.save(next);
        NurseProfile incoming = getNurseProfile(accountId);
        patient.setPatientNurseId(accountId);
        patient.setPatientNurseassign(incoming.getName());
        patientRepository.save(patient);
        handover.setStatus("ACKNOWLEDGED");
        handover.setAcknowledgedAt(Instant.now());
        return handoverRepository.save(handover);
    }

    private void autoAssignPatient(Patient patient, String actor) {
        Ward ward = findPatientWard(patient);
        String shift = currentShift();
        List<NurseProfile> eligible = rosterRepository.findByWardIdAndStatus(ward.getId(), "SCHEDULED").stream()
                .filter(roster -> shift.equals(roster.getShift()))
                .filter(roster -> covers(roster, LocalDate.now()))
                .map(roster -> nurseProfileRepository.findByAccountId(roster.getNurseId()).orElse(null))
                .filter(profile -> profile != null && "ACTIVE".equals(profile.getStatus()))
                .filter(profile -> isActiveNurseAccount(profile.getAccountId()))
                .filter(profile -> workload(profile.getAccountId(), ward.getId(), patient.getPatientId())
                        < ward.getMaxPatientsPerNurse())
                .distinct()
                .sorted(Comparator.comparingInt(profile -> workload(profile.getAccountId(), ward.getId(), patient.getPatientId())))
                .toList();
        if (eligible.isEmpty()) return;
        NurseProfile selected = eligible.get(0);
        assignPatient(patient.getPatientId(), selected.getAccountId(), "PRIMARY", shift, actor, "AUTO");
    }

    private PatientAssignment assignPatient(
            String patientId, String nurseId, String role, String shift, String actor, String source) {
        Patient patient = findPatient(patientId);
        Ward ward = findPatientWard(patient);
        NurseProfile nurse = getNurseProfile(nurseId);
        requireOnDuty(nurse, ward, shift, LocalDate.now());
        enforceWorkload(nurse, ward, patientId);
        assignmentRepository.findByPatientIdAndStatus(patientId, "ACTIVE").stream()
                .filter(existing -> role.equals(existing.getRole()))
                .forEach(this::closeAssignment);
        PatientAssignment assignment = new PatientAssignment();
        assignment.setPatientId(patientId);
        assignment.setNurseId(nurse.getAccountId());
        assignment.setWardId(ward.getId());
        assignment.setBedId(patient.getPatientBedId());
        assignment.setShift(shift);
        assignment.setRole(role);
        assignment.setAssignedBy(actor);
        assignment.setSource(source);
        if ("PRIMARY".equals(role)) {
            patient.setPatientNurseId(nurse.getAccountId());
            patient.setPatientNurseassign(nurse.getName());
            patientRepository.save(patient);
        }
        return assignmentRepository.save(assignment);
    }

    private void requireOnDuty(NurseProfile nurse, Ward ward, String shift, LocalDate date) {
        if (!"ACTIVE".equals(nurse.getStatus()) || !isActiveNurseAccount(nurse.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    nurse.getName() + " is not an active nurse and cannot receive an assignment");
        }
        boolean onDuty = rosterRepository.findByWardIdAndStatus(ward.getId(), "SCHEDULED").stream()
                .anyMatch(roster -> nurse.getAccountId().equals(roster.getNurseId())
                        && shift.equals(roster.getShift())
                        && covers(roster, date));
        if (!onDuty) throw new ResponseStatusException(HttpStatus.CONFLICT,
                nurse.getName() + " is not rostered in " + ward.getName() + " for the " + shift.toLowerCase(Locale.ROOT) + " shift today");
    }

    private void enforceWorkload(NurseProfile nurse, Ward ward, String patientId) {
        if (workload(nurse.getAccountId(), ward.getId(), patientId) >= ward.getMaxPatientsPerNurse()
                && assignmentRepository.findByPatientIdAndStatus(patientId, "ACTIVE").stream()
                .noneMatch(assignment -> nurse.getAccountId().equals(assignment.getNurseId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    nurse.getName() + " has reached this ward’s nurse-to-patient limit (" + ward.getMaxPatientsPerNurse() + ")");
        }
    }

    private int workload(String nurseId, String wardId, String patientId) {
        return (int) assignmentRepository.findByNurseIdAndStatus(nurseId, "ACTIVE").stream()
                .filter(assignment -> wardId.equals(assignment.getWardId()))
                .filter(assignment -> "PRIMARY".equals(assignment.getRole()))
                .map(PatientAssignment::getPatientId)
                .filter(id -> !StringUtils.hasText(patientId) || !id.equals(patientId))
                .distinct()
                .count();
    }

    private PatientAssignment requireActiveAssignment(String nurseId, String patientId) {
        return assignmentRepository.findByPatientIdAndStatus(patientId, "ACTIVE").stream()
                .filter(assignment -> nurseId.equals(assignment.getNurseId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "This patient is not assigned to your nurse account"));
    }

    private void closeAssignment(PatientAssignment assignment) {
        assignment.setStatus("COMPLETED");
        assignment.setToTime(Instant.now());
        assignmentRepository.save(assignment);
    }

    private void unassignUnavailableNurse(String nurseId, String actor) {
        List<PatientAssignment> activeAssignments = assignmentRepository
                .findByNurseIdAndStatus(nurseId, "ACTIVE");
        Set<String> primaryPatientIds = activeAssignments.stream()
                .filter(assignment -> "PRIMARY".equals(assignment.getRole()))
                .map(PatientAssignment::getPatientId)
                .collect(java.util.stream.Collectors.toSet());
        activeAssignments.forEach(this::closeAssignment);
        for (String patientId : primaryPatientIds) {
            Patient patient = findPatient(patientId);
            List<PatientAssignment> remainingPrimary = assignmentRepository
                    .findByPatientIdAndStatus(patientId, "ACTIVE").stream()
                    .filter(assignment -> "PRIMARY".equals(assignment.getRole()))
                    .toList();
            if (remainingPrimary.isEmpty()) {
                patient.setPatientNurseId(null);
                patient.setPatientNurseassign(null);
            } else {
                NurseProfile primary = getNurseProfile(remainingPrimary.get(0).getNurseId());
                patient.setPatientNurseId(primary.getAccountId());
                patient.setPatientNurseassign(primary.getName());
            }
            patientRepository.save(patient);
            if (remainingPrimary.isEmpty() && isAdmitted(patient)) {
                autoAssignPatient(patient, actor);
            }
        }
    }

    private NurseProfile getNurseProfile(String accountId) {
        return nurseProfileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nurse profile was not found"));
    }

    private user getNurseAccount(String accountId) {
        user account = userRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nurse account was not found"));
        if (!account.isActive() || !isNurse(account)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected account is not an active nurse");
        }
        return account;
    }

    private boolean isNurse(user account) {
        return account.getRoles().contains("NURSE") || account.getRoles().contains("HEAD_NURSE");
    }

    private Set<String> managerWardScope(String accountId) {
        user account = userRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Staff account could not be identified"));
        if (hasFullNursingAccess(account)) return null;
        if (!account.getRoles().contains("HEAD_NURSE")) return Set.of();
        LocalDate today = LocalDate.now();
        return rosterRepository.findByNurseIdAndStatus(accountId, "SCHEDULED").stream()
                .filter(roster -> !LocalDate.parse(roster.getEndDate()).isBefore(today))
                .map(NurseShiftRoster::getWardId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean hasFullNursingAccess(user account) {
        return account.getRoles().stream().anyMatch(role -> Set.of(
                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE").contains(role));
    }

    private void requireWardScope(String accountId, String wardId) {
        Set<String> wardScope = managerWardScope(accountId);
        if (wardScope != null && !wardScope.contains(wardId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This ward is outside your Head Nurse assignment");
        }
    }

    private void requireActorWardScope(String actor, String wardId) {
        user account = findActor(actor);
        if (hasFullNursingAccess(account)) return;
        if (!account.getRoles().contains("HEAD_NURSE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot manage ward assignments");
        }
        requireWardScope(account.getId(), wardId);
    }

    private void requireProfileScope(String targetAccountId, String actor) {
        user account = findActor(actor);
        if (Set.of("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN").stream()
                .anyMatch(account.getRoles()::contains)) return;
        if (!account.getRoles().contains("HEAD_NURSE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot manage nurse profiles");
        }
        if (targetAccountId.equals(account.getId())) return;
        Set<String> wardScope = managerWardScope(account.getId());
        boolean targetWorksInWard = rosterRepository.findByNurseIdAndStatus(targetAccountId, "SCHEDULED").stream()
                .anyMatch(roster -> wardScope.contains(roster.getWardId()));
        if (!targetWorksInWard) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can manage nurse profiles only for staff rostered in your ward");
        }
    }

    private user findActor(String actor) {
        return userRepository.findById(actor).or(() -> {
            List<user> matches = userRepository.findAllByEmailIdIgnoreCase(actor);
            return matches.size() == 1 ? java.util.Optional.of(matches.get(0)) : java.util.Optional.empty();
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Staff account could not be identified"));
    }

    private boolean isPatientInWards(String patientId, Set<String> wardIds) {
        return patientRepository.findAllByPatientId(patientId).stream()
                .anyMatch(patient -> wardIds.contains(patient.getPatientWardId()));
    }

    private boolean inBedRange(String bedId, String bedFrom, String bedTo) {
        if (!StringUtils.hasText(bedId)) return false;
        WardBed bed = wardBedRepository.findById(bedId).orElse(null);
        if (bed == null) return false;
        String number = bed.getBedNumber();
        if (!StringUtils.hasText(bedFrom) && !StringUtils.hasText(bedTo)) return true;
        int value = parseBedNumber(number);
        int from = StringUtils.hasText(bedFrom) ? parseBedNumber(bedFrom) : Integer.MIN_VALUE;
        int to = StringUtils.hasText(bedTo) ? parseBedNumber(bedTo) : Integer.MAX_VALUE;
        return value >= from && value <= to;
    }

    private int parseBedNumber(String value) {
        try {
            return Integer.parseInt(value.replaceAll("\\D", ""));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bed range must use numeric bed numbers");
        }
    }

    private boolean isActiveNurseAccount(String accountId) {
        return userRepository.findById(accountId)
                .filter(account -> account.isActive() && isNurse(account))
                .isPresent();
    }

    private Ward requireWard(String wardId) {
        return wardRepository.findById(wardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ward was not found"));
    }

    private Ward findPatientWard(Patient patient) {
        if (StringUtils.hasText(patient.getPatientWardId())) return requireWard(patient.getPatientWardId());
        return wardRepository.findAll().stream()
                .filter(ward -> ward.getName().equalsIgnoreCase(patient.getPatientWardnum() == null ? "" : patient.getPatientWardnum()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Patient is not assigned to a configured ward"));
    }

    private Patient findPatient(String patientId) {
        List<Patient> matches = patientRepository.findAllByPatientId(patientId);
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient ID not found");
        if (matches.size() > 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient ID is not unique");
        return matches.get(0);
    }

    private boolean isAdmitted(Patient patient) {
        return StringUtils.hasText(patient.getPatientAdmitdate()) && !StringUtils.hasText(patient.getPatientDischargedate());
    }

    private boolean covers(NurseShiftRoster roster, LocalDate date) {
        return !date.isBefore(LocalDate.parse(roster.getStartDate()))
                && !date.isAfter(LocalDate.parse(roster.getEndDate()));
    }

    private LocalDate parseDate(String value, String label) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must use YYYY-MM-DD");
        }
    }

    private String validateShift(String shift) {
        if (!StringUtils.hasText(shift)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select MORNING, EVENING, or NIGHT shift");
        }
        String value = normalize(shift);
        if (!SHIFTS.contains(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shift must be MORNING, EVENING, or NIGHT");
        return value;
    }

    private String currentShift() {
        LocalTime now = LocalTime.now();
        if (!now.isBefore(LocalTime.of(6, 0)) && now.isBefore(LocalTime.of(14, 0))) return "MORNING";
        if (!now.isBefore(LocalTime.of(14, 0)) && now.isBefore(LocalTime.of(22, 0))) return "EVENING";
        return "NIGHT";
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
