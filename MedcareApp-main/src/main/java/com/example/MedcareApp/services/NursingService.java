package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.Consultation;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.nursing.NurseHandover;
import com.example.MedcareApp.Entity.nursing.NurseProfile;
import com.example.MedcareApp.Entity.nursing.NurseShiftRoster;
import com.example.MedcareApp.Entity.nursing.NurseShiftSwap;
import com.example.MedcareApp.Entity.nursing.NursingCareRecord;
import com.example.MedcareApp.Entity.nursing.PatientAssignment;
import com.example.MedcareApp.Entity.nursing.BedStatusHistory;
import com.example.MedcareApp.Entity.nursing.BedStaySegment;
import com.example.MedcareApp.Entity.nursing.BedWaitingListEntry;
import com.example.MedcareApp.Entity.nursing.Ward;
import com.example.MedcareApp.Entity.nursing.WardBed;
import com.example.MedcareApp.Entity.nursing.WardRoom;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.services.StaffIdentifierGenerator;
import com.example.MedcareApp.Interafce.ConsultationRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.Interafce.nursing.NurseHandoverRepository;
import com.example.MedcareApp.Interafce.nursing.NurseProfileRepository;
import com.example.MedcareApp.Interafce.nursing.NurseShiftRosterRepository;
import com.example.MedcareApp.Interafce.nursing.NurseShiftSwapRepository;
import com.example.MedcareApp.Interafce.nursing.NursingCareRecordRepository;
import com.example.MedcareApp.Interafce.nursing.PatientAssignmentRepository;
import com.example.MedcareApp.Interafce.nursing.BedStatusHistoryRepository;
import com.example.MedcareApp.Interafce.nursing.BedStaySegmentRepository;
import com.example.MedcareApp.Interafce.nursing.BedWaitingListRepository;
import com.example.MedcareApp.Interafce.nursing.WardBedRepository;
import com.example.MedcareApp.Interafce.nursing.WardRepository;
import com.example.MedcareApp.Interafce.nursing.WardRoomRepository;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

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
    private final WardRoomRepository roomRepository;
    private final BedStatusHistoryRepository bedHistoryRepository;
    private final BedStaySegmentRepository bedStayRepository;
    private final BedWaitingListRepository bedWaitingListRepository;
    private final MongoTemplate mongoTemplate;

    public List<NurseProfile> getNurseProfiles() {
        return nurseProfileRepository.findAll().stream().map(profile -> {
            if (!StringUtils.hasText(profile.getEmployeeId()) || !profile.getEmployeeId().startsWith("NS-")) {
                profile.setEmployeeId(StaffIdentifierGenerator.generate("NS"));
                return nurseProfileRepository.save(profile);
            }
            return profile;
        }).toList();
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
        return getNurseProfiles().stream()
                .filter(profile -> nurseIds.contains(profile.getAccountId()))
                .toList();
    }

    public NurseProfile saveNurseProfile(String accountId, NurseProfile profile, String actor) {
        requireProfileScope(accountId, actor);
        user account = getNurseAccount(accountId);
        if (profile == null || !StringUtils.hasText(profile.getName())
                || !StringUtils.hasText(profile.getLicenseNumber())
                || !StringUtils.hasText(profile.getDesignation())
                || !StringUtils.hasText(profile.getSpecialization())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name, license number, designation, and specialization are required");
        }
        String designation = normalize(profile.getDesignation());
        if (!DESIGNATIONS.contains(designation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid nurse designation");
        }
        boolean duplicateProfile = nurseProfileRepository.findAll().stream()
                .filter(existing -> !account.getId().equals(existing.getAccountId()))
                .anyMatch(existing -> profile.getLicenseNumber().trim().equalsIgnoreCase(existing.getLicenseNumber()));
        if (duplicateProfile) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "License number must be unique across nurse profiles");
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
        saved.setEmployeeId(StringUtils.hasText(saved.getEmployeeId()) && saved.getEmployeeId().startsWith("NS-")
                ? saved.getEmployeeId() : StaffIdentifierGenerator.generate("NS"));
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

    public List<WardRoom> getRooms(String wardId, String accountId) {
        expireReservations();
        Set<String> wardScope = managerWardScope(accountId);
        if (StringUtils.hasText(wardId)) {
            requireWardScope(accountId, wardId);
            return roomRepository.findByWardId(wardId);
        }
        return roomRepository.findAll().stream()
                .filter(room -> wardScope == null || wardScope.contains(room.getWardId()))
                .toList();
    }

    public WardRoom saveRoom(WardRoom room, String actor) {
        if (room == null || !StringUtils.hasText(room.getWardId()) || !StringUtils.hasText(room.getRoomNumber())
                || !StringUtils.hasText(room.getAcType()) || !StringUtils.hasText(room.getCategory())
                || room.getBedCapacity() < 1 || room.getBedCapacity() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ward, room number, AC type, category, and bed capacity are required");
        }
        requireActorWardScope(actor, room.getWardId());
        requireWard(room.getWardId());
        String roomNumber = room.getRoomNumber().trim();
        roomRepository.findByRoomNumberIgnoreCase(roomNumber)
                .filter(existing -> room.getId() == null || !existing.getId().equals(room.getId()))
                .ifPresent(existing -> { throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "That room number already exists in this hospital"); });

        String acType = normalize(room.getAcType());
        if (!Set.of("AC", "NON_AC").contains(acType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AC type must be AC or NON_AC");
        }
        String genderRestriction = StringUtils.hasText(room.getGenderRestriction())
                ? normalize(room.getGenderRestriction()) : "ANY";
        if (!Set.of("ANY", "MALE", "FEMALE").contains(genderRestriction)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gender restriction must be Any, Male, or Female");
        }
        String status = StringUtils.hasText(room.getStatus()) ? normalize(room.getStatus()) : "ACTIVE";
        if (!Set.of("ACTIVE", "UNDER_MAINTENANCE", "CLOSED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an active, maintenance, or closed room status");
        }
        String defaultBedType = StringUtils.hasText(room.getDefaultBedType())
                ? normalize(room.getDefaultBedType()) : "STANDARD";
        if (!Set.of("STANDARD", "ELECTRIC", "ICU", "PEDIATRIC_COT", "BARIATRIC", "STRETCHER", "INCUBATOR")
                .contains(defaultBedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a supported default bed type");
        }
        List<WardBed> roomBeds = room.getId() == null ? List.of() : wardBedRepository.findByRoomId(room.getId());
        if (roomBeds.size() > room.getBedCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bed capacity cannot be less than beds already created");
        }
        if (!"ACTIVE".equals(status) && roomBeds.stream()
                .anyMatch(bed -> Set.of("OCCUPIED", "RESERVED").contains(bed.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A room with occupied or reserved beds cannot be closed or placed under maintenance");
        }
        boolean isNew = room.getId() == null || roomRepository.findById(room.getId()).isEmpty();
        int bedStartIndex = roomBeds.size();
        for (int index = isNew ? 0 : bedStartIndex; index < room.getBedCapacity(); index++) {
            String bedNumber = generatedBedNumber(roomNumber, room.getBedCapacity(), index);
            if (wardBedRepository.findByWardIdAndBedNumber(room.getWardId(), bedNumber).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bed " + bedNumber + " already exists in this ward");
            }
        }
        if (isNew) room.setId(null);
        room.setRoomNumber(roomNumber);
        room.setAcType(acType);
        room.setGenderRestriction(genderRestriction);
        room.setCategory(room.getCategory().trim().toUpperCase(Locale.ROOT));
        room.setStatus(status);
        room.setDefaultBedType(defaultBedType);
        room.setBuilding(trimToNull(room.getBuilding()));
        room.setFloor(trimToNull(room.getFloor()));
        room.setNotes(trimToNull(room.getNotes()));
        room.setAmenities(room.getAmenities() == null ? List.of()
                : room.getAmenities().stream().filter(StringUtils::hasText).map(String::trim).distinct().toList());
        WardRoom saved = roomRepository.save(room);
        if (isNew || bedStartIndex < room.getBedCapacity()) createRoomBeds(saved, actor, isNew ? 0 : bedStartIndex);
        if ("ACTIVE".equals(status)) {
            wardBedRepository.findByRoomId(saved.getId()).stream()
                    .filter(bed -> "MAINTENANCE".equals(bed.getStatus()))
                    .filter(bed -> bed.getBlockReason() != null && bed.getBlockReason().startsWith("Room status:"))
                    .forEach(bed -> changeBedStatus(bed, "VACANT", actor, "Room reopened"));
        } else {
            wardBedRepository.findByRoomId(saved.getId()).stream()
                    .filter(bed -> "VACANT".equals(bed.getStatus()))
                    .forEach(bed -> {
                        bed.setBlockReason("Room status: " + status);
                        changeBedStatus(bed, "MAINTENANCE", actor, "Room status changed to " + status);
                    });
        }
        return saved;
    }

    public WardRoom updateRoom(String roomId, WardRoom update, String actor) {
        if (update == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room details are required");
        }
        WardRoom existing = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room was not found"));
        if (!existing.getWardId().equals(update.getWardId())
                && !wardBedRepository.findByRoomId(roomId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A room with beds cannot be moved to a different ward");
        }
        update.setId(existing.getId());
        return saveRoom(update, actor);
    }

    public List<WardRoom> bulkCreateRooms(List<WardRoom> rooms, String actor) {
        if (rooms == null || rooms.isEmpty() || rooms.size() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Create between 1 and 200 rooms at a time");
        }
        Set<String> roomNumbers = new java.util.HashSet<>();
        Set<String> generatedBedNumbers = new java.util.HashSet<>();
        for (WardRoom room : rooms) {
            if (room == null || !StringUtils.hasText(room.getRoomNumber())
                    || !roomNumbers.add(room.getRoomNumber().trim().toUpperCase(Locale.ROOT))
                    || roomRepository.findByRoomNumberIgnoreCase(room.getRoomNumber().trim()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Room numbers in a bulk request must be unique and not already in use");
            }
            if (room.getId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bulk room creation does not accept room IDs");
            }
            if (!StringUtils.hasText(room.getWardId()) || !StringUtils.hasText(room.getAcType())
                    || !StringUtils.hasText(room.getCategory()) || room.getBedCapacity() < 1
                    || room.getBedCapacity() > 100
                    || !Set.of("AC", "NON_AC").contains(normalize(room.getAcType()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every room needs a ward, valid category, AC type, and bed capacity");
            }
            if (StringUtils.hasText(room.getGenderRestriction())
                    && !Set.of("ANY", "MALE", "FEMALE").contains(normalize(room.getGenderRestriction()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select Any, Male, or Female for the gender restriction");
            }
            if (StringUtils.hasText(room.getDefaultBedType())
                    && !Set.of("STANDARD", "ELECTRIC", "ICU", "PEDIATRIC_COT", "BARIATRIC", "STRETCHER", "INCUBATOR")
                        .contains(normalize(room.getDefaultBedType()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a supported default bed type");
            }
            requireActorWardScope(actor, room.getWardId());
            requireWard(room.getWardId());
            for (int index = 0; index < room.getBedCapacity(); index++) {
                String bedNumber = generatedBedNumber(room.getRoomNumber().trim(), room.getBedCapacity(), index);
                String bedKey = room.getWardId() + ":" + bedNumber.toUpperCase(Locale.ROOT);
                if (!generatedBedNumbers.add(bedKey)
                        || wardBedRepository.findByWardIdAndBedNumber(room.getWardId(), bedNumber).isPresent()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Bed " + bedNumber + " already exists in this ward");
                }
            }
        }

        return rooms.stream().map(room -> saveRoom(room, actor)).toList();
    }

    public List<Map<String, Object>> getAvailableBeds(
            String accountId, String wardId, String floor, String acType, String category, String gender, List<String> amenities) {
        expireReservations();
        user account = userRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Staff account could not be identified"));
        Set<String> wardScope = account.getRoles().contains("RECEPTIONIST")
                ? null : managerWardScope(accountId);
        if (StringUtils.hasText(wardId)) requireWardScope(accountId, wardId);
        return wardBedRepository.findAll().stream()
                .filter(bed -> "VACANT".equals(bed.getStatus()))
                .filter(bed -> wardScope == null || wardScope.contains(bed.getWardId()))
                .filter(bed -> !StringUtils.hasText(wardId) || wardId.equals(bed.getWardId()))
                .flatMap(bed -> {
                    WardRoom room = StringUtils.hasText(bed.getRoomId())
                            ? roomRepository.findById(bed.getRoomId()).orElse(null) : null;
                    boolean hasRoomFilters = StringUtils.hasText(floor) || StringUtils.hasText(acType)
                            || StringUtils.hasText(category) || (amenities != null && !amenities.isEmpty());
                    if (room == null && hasRoomFilters) return java.util.stream.Stream.empty();
                    if (room != null && (!"ACTIVE".equals(room.getStatus())
                            || (StringUtils.hasText(floor) && !floor.equalsIgnoreCase(room.getFloor()))
                            || (StringUtils.hasText(acType) && !normalize(acType).equals(room.getAcType()))
                            || (StringUtils.hasText(category) && !normalize(category).equals(room.getCategory()))
                            || (StringUtils.hasText(gender) && !roomAcceptsGender(room, bed, gender))
                            || (amenities != null && amenities.stream().anyMatch(requested ->
                                room.getAmenities().stream().noneMatch(existing -> existing.equalsIgnoreCase(requested)))))) {
                        return java.util.stream.Stream.empty();
                    }
                    Map<String, Object> result = new HashMap<>();
                    result.put("bed", bed);
                    result.put("room", room == null ? Map.of() : room);
                    return java.util.stream.Stream.of(result);
                })
                .toList();
    }

    public Map<String, Object> getBedSummary(String accountId) {
        expireReservations();
        Set<String> wardScope = managerWardScope(accountId);
        List<WardBed> beds = wardBedRepository.findAll().stream()
                .filter(bed -> wardScope == null || wardScope.contains(bed.getWardId()))
                .toList();
        Map<String, Long> counts = new HashMap<>();
        for (String status : List.of("VACANT", "RESERVED", "OCCUPIED", "CLEANING", "MAINTENANCE", "BLOCKED")) {
            counts.put(status.toLowerCase(Locale.ROOT), beds.stream().filter(bed -> status.equals(bed.getStatus())).count());
        }
        counts.put("total", (long) beds.size());
        counts.put("occupancyPercent", beds.isEmpty() ? 0L : Math.round(100.0 * counts.get("occupied") / beds.size()));
        Map<String, Object> summary = new HashMap<>();
        summary.putAll(counts);
        return summary;
    }

    public List<BedStatusHistory> getBedHistory(String bedId, String accountId) {
        WardBed bed = wardBedRepository.findById(bedId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bed was not found"));
        requireWardScope(accountId, bed.getWardId());
        return bedHistoryRepository.findByBedIdOrderByChangedAtDesc(bedId);
    }

    public List<BedStaySegment> getBedStaySegments(String accountId) {
        Set<String> wardScope = managerWardScope(accountId);
        return bedStayRepository.findAll().stream()
                .filter(segment -> wardScope == null || wardScope.contains(segment.getWardId()))
                .sorted(Comparator.comparing(BedStaySegment::getFromTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<Map<String, Object>> getBedWaitingList(String accountId) {
        user account = userRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Staff account could not be identified"));
        Set<String> wardScope = account.getRoles().contains("RECEPTIONIST")
                ? null : managerWardScope(accountId);
        return bedWaitingListRepository.findByStatusOrderByRequestedAtAsc("WAITING").stream()
                .filter(entry -> wardScope == null || wardScope.contains(entry.getWardId()))
                .map(entry -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("entry", entry);
                    Patient patient = findPatient(entry.getPatientId());
                    result.put("matchingBedsAvailable", !getAvailableBeds(accountId, entry.getWardId(), null,
                            entry.getPreferredAcType(), entry.getPreferredCategory(), patient.getGender(), null).isEmpty());
                    return result;
                }).toList();
    }

    public BedWaitingListEntry addToBedWaitingList(
            String patientId, String wardId, String acType, String category, String priority, String actor) {
        if (!StringUtils.hasText(patientId) || !StringUtils.hasText(wardId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient and preferred ward are required");
        }
        requireAdmissionDeskAccess(actor, wardId);
        requireWard(wardId);
        Patient patient = findPatient(patientId);
        if (isAdmitted(patient)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An admitted patient cannot be added to the bed waiting list");
        }
        if (bedWaitingListRepository.findByStatusOrderByRequestedAtAsc("WAITING").stream()
                .anyMatch(entry -> patientId.equals(entry.getPatientId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This patient is already on the bed waiting list");
        }
        if (StringUtils.hasText(acType) && !Set.of("AC", "NON_AC").contains(normalize(acType))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preferred AC type must be AC or NON_AC");
        }
        String normalizedPriority = StringUtils.hasText(priority) ? normalize(priority) : "NORMAL";
        if (!Set.of("NORMAL", "URGENT").contains(normalizedPriority)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select Normal or Urgent priority");
        }
        BedWaitingListEntry entry = new BedWaitingListEntry();
        entry.setPatientId(patient.getPatientId());
        entry.setPatientName(patient.getPatientName());
        entry.setWardId(wardId);
        entry.setPreferredAcType(StringUtils.hasText(acType) ? normalize(acType) : null);
        entry.setPreferredCategory(trimToNull(category));
        entry.setPriority(normalizedPriority);
        entry.setStatus("WAITING");
        entry.setRequestedAt(Instant.now());
        entry.setCreatedBy(actor);
        return bedWaitingListRepository.save(entry);
    }

    public BedWaitingListEntry cancelBedWaitingList(String entryId, String actor) {
        BedWaitingListEntry entry = bedWaitingListRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Waiting-list entry was not found"));
        requireAdmissionDeskAccess(actor, entry.getWardId());
        if (!"WAITING".equals(entry.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This waiting-list entry is no longer active");
        }
        entry.setStatus("CANCELLED");
        return bedWaitingListRepository.save(entry);
    }

    public List<WardBed> getBeds(String wardId, String accountId) {
        requireWardScope(accountId, wardId);
        expireReservations();
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
        if (StringUtils.hasText(bed.getRoomId())) {
            WardRoom room = roomRepository.findById(bed.getRoomId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected room was not found"));
            if (!wardId.equals(room.getWardId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected room does not belong to this ward");
            }
            if (wardBedRepository.findByRoomId(room.getId()).size() >= room.getBedCapacity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This room has reached its bed capacity");
            }
        }
        bed.setHoldUntil(null);
        bed.setBlockReason(null);
        return wardBedRepository.save(bed);
    }

    public WardBed updateBedStatus(String wardId, String bedId, String status, String reason, String holdUntil, String actor) {
        requireActorWardScope(actor, wardId);
        WardBed bed = wardBedRepository.findById(bedId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bed was not found"));
        if (!wardId.equals(bed.getWardId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bed was not found in this ward");
        }
        String normalizedStatus = normalize(status);
        if ("OCCUPIED".equals(bed.getStatus()) || "CLEANING".equals(bed.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Occupied beds can only be released by a patient transfer or discharge; cleaning beds must be completed by housekeeping");
        }
        if ("RESERVED".equals(normalizedStatus)) {
            if (!"VACANT".equals(bed.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a vacant bed can be reserved");
            }
            if (StringUtils.hasText(bed.getRoomId()) && roomRepository.findById(bed.getRoomId())
                    .filter(room -> "ACTIVE".equals(room.getStatus())).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Beds in inactive rooms cannot be reserved");
            }
            if (!StringUtils.hasText(holdUntil)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation expiry time is required");
            }
            Instant expiry;
            try {
                expiry = Instant.parse(holdUntil);
            } catch (RuntimeException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation expiry must be an ISO-8601 timestamp");
            }
            if (!expiry.isAfter(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation expiry must be in the future");
            }
            bed.setHoldUntil(expiry.toString());
        } else if ("VACANT".equals(normalizedStatus)) {
            if (!Set.of("RESERVED", "MAINTENANCE", "BLOCKED").contains(bed.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only reserved, blocked, or maintenance beds can be released");
            }
            bed.setHoldUntil(null);
            bed.setBlockReason(null);
        } else if ("MAINTENANCE".equals(normalizedStatus) || "BLOCKED".equals(normalizedStatus)) {
            if (!"VACANT".equals(bed.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a vacant bed can be blocked or placed under maintenance");
            }
            if (!StringUtils.hasText(reason)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required for blocking or maintenance");
            }
            bed.setBlockReason(reason.trim());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bed status must be RESERVED, VACANT, BLOCKED, or MAINTENANCE");
        }
        return changeBedStatus(bed, normalizedStatus, actor, reason);
    }

    public WardBed completeBedCleaning(String bedId, String actor) {
        WardBed bed = wardBedRepository.findById(bedId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bed was not found"));
        requireActorWardScope(actor, bed.getWardId());
        if (!"CLEANING".equals(bed.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only beds marked for cleaning can be released");
        }
        return changeBedStatus(bed, "VACANT", actor, "Housekeeping completed");
    }

    private WardBed changeBedStatus(WardBed bed, String status, String actor, String reason) {
        String previous = bed.getStatus();
        Query query = Query.query(Criteria.where("_id").is(bed.getId()).and("status").is(previous));
        Update update = new Update().set("status", status);
        if ("VACANT".equals(status) || "CLEANING".equals(status)) {
            update.unset("patientId").unset("holdUntil").unset("blockReason");
        } else if ("RESERVED".equals(status)) {
            update.set("holdUntil", bed.getHoldUntil());
        } else if ("BLOCKED".equals(status) || "MAINTENANCE".equals(status)) {
            update.set("blockReason", bed.getBlockReason());
        }
        WardBed saved = mongoTemplate.findAndModify(query, update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), WardBed.class);
        if (saved == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bed status changed while this request was being processed; refresh and try again");
        }
        bed.setStatus(status);
        BedStatusHistory history = new BedStatusHistory();
        history.setBedId(bed.getId());
        history.setWardId(bed.getWardId());
        history.setFromStatus(previous);
        history.setToStatus(status);
        history.setChangedBy(actor);
        history.setReason(trimToNull(reason));
        history.setChangedAt(Instant.now());
        bedHistoryRepository.save(history);
        return saved;
    }

    private WardBed claimVacantBed(WardBed bed, String patientId, String actor) {
        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(bed.getId()).and("status").is("VACANT")),
                new Update().set("status", "OCCUPIED").set("patientId", patientId),
                WardBed.class);
        if (result.getModifiedCount() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Selected bed is no longer vacant. Refresh the bed board and choose another bed");
        }
        BedStatusHistory history = new BedStatusHistory();
        history.setBedId(bed.getId());
        history.setWardId(bed.getWardId());
        history.setFromStatus("VACANT");
        history.setToStatus("OCCUPIED");
        history.setChangedBy(actor);
        history.setReason("Patient admitted or transferred");
        history.setChangedAt(Instant.now());
        bedHistoryRepository.save(history);
        bed.setStatus("OCCUPIED");
        bed.setPatientId(patientId);
        return bed;
    }

    private void expireReservations() {
        Instant now = Instant.now();
        wardBedRepository.findAll().stream()
                .filter(bed -> "RESERVED".equals(bed.getStatus()) && StringUtils.hasText(bed.getHoldUntil()))
                .filter(bed -> {
                    try { return !Instant.parse(bed.getHoldUntil()).isAfter(now); }
                    catch (RuntimeException exception) { return false; }
                })
                .forEach(bed -> changeBedStatus(bed, "VACANT", "reservation-expiry", "Reservation hold expired"));
    }

    private void createRoomBeds(WardRoom room, String actor, int startIndex) {
        for (int index = startIndex; index < room.getBedCapacity(); index++) {
            WardBed bed = new WardBed();
            bed.setId(null);
            bed.setWardId(room.getWardId());
            bed.setRoomId(room.getId());
            bed.setBedNumber(generatedBedNumber(room.getRoomNumber(), room.getBedCapacity(), index));
            bed.setBedType(room.getDefaultBedType());
            bed.setStatus("VACANT");
            wardBedRepository.save(bed);
            BedStatusHistory history = new BedStatusHistory();
            history.setBedId(bed.getId());
            history.setWardId(bed.getWardId());
            history.setFromStatus(null);
            history.setToStatus("VACANT");
            history.setChangedBy(actor);
            history.setReason("Bed created with room");
            history.setChangedAt(Instant.now());
            bedHistoryRepository.save(history);
        }
    }

    private String generatedBedNumber(String roomNumber, int bedCapacity, int index) {
        return bedCapacity == 1 ? roomNumber : roomNumber + "-" + bedLabel(index);
    }

    private String bedLabel(int index) {
        if (index < 26) return String.valueOf((char) ('A' + index));
        return "BED-" + (index + 1);
    }

    private void validateRoomCompatibility(Patient patient, WardBed bed) {
        if (!StringUtils.hasText(bed.getRoomId())) return;
        WardRoom room = roomRepository.findById(bed.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Bed room configuration is missing"));
        if (!"ACTIVE".equals(room.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The selected room is not active for admission");
        }
        String patientGender = normalizedGender(patient.getGender());
        if (!roomAcceptsGender(room, bed, patientGender)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Patient gender does not match the room restriction or existing shared-room occupants");
        }
    }

    private boolean roomAcceptsGender(WardRoom room, WardBed targetBed, String gender) {
        String patientGender = normalizedGender(gender);
        String restriction = StringUtils.hasText(room.getGenderRestriction())
                ? normalize(room.getGenderRestriction()) : "ANY";
        if (!"ANY".equals(restriction) && !restriction.equals(patientGender)) return false;
        if (!StringUtils.hasText(patientGender)) return true;
        Set<String> occupantGenders = wardBedRepository.findByRoomId(room.getId()).stream()
                .filter(bed -> "OCCUPIED".equals(bed.getStatus()))
                .filter(bed -> !bed.getId().equals(targetBed.getId()))
                .map(WardBed::getPatientId)
                .filter(StringUtils::hasText)
                .flatMap(id -> patientRepository.findAllByPatientId(id).stream())
                .map(Patient::getGender)
                .filter(StringUtils::hasText)
                .map(this::normalizedGender)
                .collect(java.util.stream.Collectors.toSet());
        return occupantGenders.isEmpty() || occupantGenders.contains(patientGender);
    }

    private String normalizedGender(String gender) {
        if (!StringUtils.hasText(gender)) return "";
        String normalized = normalize(gender);
        if (Set.of("M", "MALE").contains(normalized)) return "MALE";
        if (Set.of("F", "FEMALE").contains(normalized)) return "FEMALE";
        return normalized;
    }

    private void startBedStay(Patient patient, WardBed bed, String actor, String reason) {
        BedStaySegment segment = new BedStaySegment();
        segment.setPatientId(patient.getPatientId());
        segment.setWardId(bed.getWardId());
        segment.setRoomId(bed.getRoomId());
        segment.setBedId(bed.getId());
        segment.setFromTime(Instant.now());
        segment.setAllocatedBy(actor);
        segment.setReason(reason);
        segment.setStatus("ACTIVE");
        bedStayRepository.save(segment);
    }

    private void closeBedStay(String patientId, String actor, String reason) {
        Instant now = Instant.now();
        bedStayRepository.findByStatus("ACTIVE").stream()
                .filter(segment -> patientId.equals(segment.getPatientId()))
                .forEach(segment -> {
                    segment.setToTime(now);
                    segment.setAllocatedBy(actor);
                    segment.setReason(reason);
                    segment.setStatus("CLOSED");
                    bedStayRepository.save(segment);
                });
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
        expireReservations();
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
                item.put("bedType", bed.getBedType());
                item.put("roomId", bed.getRoomId() == null ? "" : bed.getRoomId());
                item.put("equipment", bed.getEquipment() == null ? Map.of() : bed.getEquipment());
                item.put("blockReason", bed.getBlockReason() == null ? "" : bed.getBlockReason());
                item.put("holdUntil", bed.getHoldUntil() == null ? "" : bed.getHoldUntil());
                item.put("room", StringUtils.hasText(bed.getRoomId()) ? roomRepository.findById(bed.getRoomId()).map(room -> Map.of(
                        "id", room.getId(), "roomNumber", room.getRoomNumber(), "floor", room.getFloor() == null ? "" : room.getFloor(),
                        "building", room.getBuilding() == null ? "" : room.getBuilding(), "acType", room.getAcType(),
                        "category", room.getCategory(), "status", room.getStatus(),
                        "genderRestriction", room.getGenderRestriction() == null ? "ANY" : room.getGenderRestriction(),
                        "amenities", room.getAmenities() == null ? List.of() : room.getAmenities())).orElse(Map.of()) : Map.of());
                admitted.stream().filter(patient -> bed.getId().equals(patient.getPatientBedId())).findFirst()
                        .ifPresent(patient -> item.put("patientName", patient.getPatientName()));
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
            item.put("occupiedBeds", beds.stream().filter(bed -> "OCCUPIED".equals(bed.getStatus())).count());
            item.put("reservedBeds", beds.stream().filter(bed -> "RESERVED".equals(bed.getStatus())).count());
            item.put("cleaningBeds", beds.stream().filter(bed -> "CLEANING".equals(bed.getStatus())).count());
            item.put("maintenanceBeds", beds.stream().filter(bed -> "MAINTENANCE".equals(bed.getStatus())).count());
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
        validateRoomCompatibility(patient, bed);
        patient.setPatientAdmitdate(LocalDate.now().toString());
        patient.setPatientDischargedate(null);
        patient.setPatientWardnum(ward.getName());
        patient.setPatientWardId(ward.getId());
        patient.setPatientBedId(bed.getId());
        claimVacantBed(bed, patientId, actor);
        startBedStay(patient, bed, actor, "Admission");
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
        validateRoomCompatibility(patient, targetBed);
        claimVacantBed(targetBed, patientId, actor);
        closeBedStay(patientId, actor, "Patient transferred");
        if (patient.getPatientBedId() != null) {
            wardBedRepository.findById(patient.getPatientBedId()).ifPresent(previousBed -> {
                if (patientId.equals(previousBed.getPatientId())) {
                    changeBedStatus(previousBed, "CLEANING", actor, "Patient transferred to another bed");
                }
            });
        }
        assignmentRepository.findByPatientIdAndStatus(patientId, "ACTIVE").forEach(this::closeAssignment);
        patient.setPatientWardId(targetWard.getId());
        patient.setPatientWardnum(targetWard.getName());
        patient.setPatientBedId(targetBed.getId());
        patient.setPatientNurseId(null);
        patient.setPatientNurseassign(null);
        startBedStay(patient, targetBed, actor, "Patient transferred");
        patientRepository.save(patient);
        autoAssignPatient(patient, actor);
        return patientRepository.save(patient);
    }

    public void dischargePatient(Patient patient) {
        dischargePatient(patient, "discharge-service");
    }

    public void dischargePatient(Patient patient, String actor) {
        if (patient.getPatientBedId() != null) {
            wardBedRepository.findById(patient.getPatientBedId()).ifPresent(bed -> {
                if (patient.getPatientId().equals(bed.getPatientId())) {
                    changeBedStatus(bed, "CLEANING", actor, "Patient discharged; housekeeping required");
                }
            });
        }
        closeBedStay(patient.getPatientId(), actor, "Patient discharged");
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
                    WardBed bed = patient.getPatientBedId() == null ? null
                            : wardBedRepository.findById(patient.getPatientBedId()).orElse(null);
                    if (bed != null) {
                        Map<String, Object> bedDetails = new HashMap<>();
                        bedDetails.put("id", bed.getId());
                        bedDetails.put("bedNumber", bed.getBedNumber());
                        bedDetails.put("status", bed.getStatus());
                        if (StringUtils.hasText(bed.getRoomId())) {
                            roomRepository.findById(bed.getRoomId()).ifPresent(room -> bedDetails.put("room", room));
                        }
                        row.put("bed", bedDetails);
                    } else {
                        row.put("bed", null);
                    }
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

    private void requireAdmissionDeskAccess(String actor, String wardId) {
        user account = findActor(actor);
        boolean admissionDesk = account.getRoles().stream().anyMatch(role -> Set.of(
                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE", "RECEPTIONIST").contains(role));
        if (admissionDesk) return;
        if (account.getRoles().contains("HEAD_NURSE")) {
            requireWardScope(account.getId(), wardId);
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot manage the bed waiting list");
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
