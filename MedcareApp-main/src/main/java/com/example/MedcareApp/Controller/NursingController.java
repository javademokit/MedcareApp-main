package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.nursing.NurseHandover;
import com.example.MedcareApp.Entity.nursing.BedStaySegment;
import com.example.MedcareApp.Entity.nursing.BedWaitingListEntry;
import com.example.MedcareApp.Entity.nursing.NurseProfile;
import com.example.MedcareApp.Entity.nursing.NurseShiftRoster;
import com.example.MedcareApp.Entity.nursing.NurseShiftSwap;
import com.example.MedcareApp.Entity.nursing.NursingCareRecord;
import com.example.MedcareApp.Entity.nursing.PatientAssignment;
import com.example.MedcareApp.Entity.nursing.Ward;
import com.example.MedcareApp.Entity.nursing.WardBed;
import com.example.MedcareApp.Entity.nursing.WardRoom;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.services.NursingService;
import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/nursing")
public class NursingController {
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final NursingService nursingService;

    public NursingController(
            PatientRepository patientRepository,
            UserRepository userRepository,
            NursingService nursingService) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.nursingService = nursingService;
    }

    @GetMapping("/nurses")
    public List<Map<String, Object>> getNurses(Principal principal) {
        user currentAccount = getAccount(principal);
        boolean headNurseOnly = currentAccount.getRoles().contains("HEAD_NURSE")
                && currentAccount.getRoles().stream().noneMatch(role -> List.of(
                        "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE").contains(role));
        boolean mayViewFullProfiles = currentAccount.getRoles().stream().anyMatch(role -> List.of(
                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HEAD_NURSE").contains(role));
        List<NurseProfile> visibleProfiles = headNurseOnly
                ? nursingService.getNurseProfilesForManager(currentAccount.getId())
                : nursingService.getNurseProfiles();
        Map<String, NurseProfile> profiles = visibleProfiles.stream()
                .collect(java.util.stream.Collectors.toMap(NurseProfile::getAccountId, profile -> profile));
        return userRepository.findAll().stream()
                .filter(account -> account.isActive()
                        && (account.getRoles().contains("NURSE") || account.getRoles().contains("HEAD_NURSE")))
                .filter(account -> !headNurseOnly || profiles.containsKey(account.getId()))
                .sorted(Comparator.comparing(user::getUserId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(account -> {
                    NurseProfile profile = profiles.get(account.getId());
                    return Map.<String, Object>of(
                            "id", account.getId(),
                            "userId", account.getUserId() == null ? "" : account.getUserId(),
                            "emailId", account.getEmailId() == null ? "" : account.getEmailId(),
                            "mobileNo", account.getMobileNo() == null ? "" : account.getMobileNo(),
                            "profile", profile == null || !mayViewFullProfiles ? Map.of() : profile,
                            "profileComplete", profile != null,
                            "status", profile == null ? "PROFILE_REQUIRED" : profile.getStatus(),
                            "name", profile == null ? account.getUserId() : profile.getName());
                }).toList();
    }

    @PutMapping("/nurses/{accountId}/profile")
    public NurseProfile saveNurseProfile(
            @PathVariable String accountId, @RequestBody NurseProfile profile, Principal principal) {
        return nursingService.saveNurseProfile(accountId, profile, principal.getName());
    }

    @GetMapping("/wards")
    public List<Map<String, Object>> getWards(Principal principal) {
        return nursingService.getWardOccupancy(getAccount(principal).getId());
    }

    @PostMapping("/wards")
    public ResponseEntity<Ward> createWard(@RequestBody Ward ward) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nursingService.saveWard(ward));
    }

    @PutMapping("/wards/{wardId}")
    public Ward updateWard(@PathVariable String wardId, @RequestBody Ward ward) {
        ward.setId(wardId);
        return nursingService.saveWard(ward);
    }

    @GetMapping("/wards/{wardId}/beds")
    public List<WardBed> getBeds(@PathVariable String wardId, Principal principal) {
        return nursingService.getBeds(wardId, getAccount(principal).getId());
    }

    @PostMapping("/wards/{wardId}/beds")
    public ResponseEntity<WardBed> addBed(
            @PathVariable String wardId, @RequestBody WardBed bed, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(nursingService.addBed(wardId, bed, principal.getName()));
    }

    @PutMapping("/wards/{wardId}/beds/{bedId}")
    public WardBed updateBedStatus(
            @PathVariable String wardId,
            @PathVariable String bedId,
            @RequestBody BedStatusRequest request,
            Principal principal) {
        if (request == null || !StringUtils.hasText(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bed status is required");
        }
        return nursingService.updateBedStatus(wardId, bedId, request.status(),
                request.reason(), request.holdUntil(), principal.getName());
    }

    @GetMapping("/rooms")
    public List<WardRoom> getRooms(
            @RequestParam(required = false) String wardId,
            Principal principal) {
        return nursingService.getRooms(wardId, getAccount(principal).getId());
    }

    @PostMapping("/rooms")
    public ResponseEntity<WardRoom> createRoom(@RequestBody WardRoom room, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(nursingService.saveRoom(room, principal.getName()));
    }

    @PostMapping("/rooms/bulk")
    public ResponseEntity<List<WardRoom>> createRoomsBulk(
            @RequestBody List<WardRoom> rooms, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(nursingService.bulkCreateRooms(rooms, principal.getName()));
    }

    @PutMapping("/rooms/{roomId}")
    public WardRoom updateRoom(
            @PathVariable String roomId, @RequestBody WardRoom room, Principal principal) {
        return nursingService.updateRoom(roomId, room, principal.getName());
    }

    @GetMapping("/beds/available")
    public List<Map<String, Object>> getAvailableBeds(
            @RequestParam(required = false) String wardId,
            @RequestParam(required = false) String floor,
            @RequestParam(required = false) String ac,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) List<String> amenities,
            Principal principal) {
        return nursingService.getAvailableBeds(
                getAccount(principal).getId(), wardId, floor, ac, category, gender, amenities);
    }

    @GetMapping("/beds/summary")
    public Map<String, Object> getBedSummary(Principal principal) {
        return nursingService.getBedSummary(getAccount(principal).getId());
    }

    @GetMapping("/bed-stays")
    public List<BedStaySegment> getBedStaySegments(Principal principal) {
        return nursingService.getBedStaySegments(getAccount(principal).getId());
    }

    @GetMapping("/bed-waiting-list")
    public List<Map<String, Object>> getBedWaitingList(Principal principal) {
        return nursingService.getBedWaitingList(getAccount(principal).getId());
    }

    @PostMapping("/bed-waiting-list")
    public ResponseEntity<BedWaitingListEntry> addToBedWaitingList(
            @RequestBody BedWaitingListRequest request, Principal principal) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waiting-list details are required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(nursingService.addToBedWaitingList(
                request.patientId(), request.wardId(), request.preferredAcType(),
                request.preferredCategory(), request.priority(), principal.getName()));
    }

    @PostMapping("/bed-waiting-list/{entryId}/cancel")
    public BedWaitingListEntry cancelBedWaitingList(@PathVariable String entryId, Principal principal) {
        return nursingService.cancelBedWaitingList(entryId, principal.getName());
    }

    @GetMapping("/beds/{bedId}/history")
    public List<com.example.MedcareApp.Entity.nursing.BedStatusHistory> getBedHistory(
            @PathVariable String bedId, Principal principal) {
        return nursingService.getBedHistory(bedId, getAccount(principal).getId());
    }

    @PostMapping("/beds/{bedId}/cleaning-complete")
    public WardBed completeBedCleaning(@PathVariable String bedId, Principal principal) {
        return nursingService.completeBedCleaning(bedId, principal.getName());
    }

    @GetMapping("/rosters")
    public List<NurseShiftRoster> getRosters(Principal principal) {
        return nursingService.getRosters(getAccount(principal).getId());
    }

    @GetMapping("/rosters/mine")
    public List<NurseShiftRoster> getMyRosters(Principal principal) {
        return nursingService.getNurseRosters(getAccount(principal).getId());
    }

    @PostMapping("/rosters")
    public ResponseEntity<NurseShiftRoster> createRoster(
            @RequestBody NurseShiftRoster roster,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(nursingService.saveRoster(roster, principal.getName()));
    }

    @GetMapping("/shift-swaps")
    public List<NurseShiftSwap> getShiftSwaps(Principal principal) {
        user account = getAccount(principal);
        boolean manager = account.getRoles().stream()
                .map(role -> role.toUpperCase())
                .anyMatch(role -> List.of("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE",
                        "HEAD_NURSE").contains(role));
        return nursingService.getShiftSwaps(account.getId(), manager);
    }

    @PostMapping("/shift-swaps")
    public ResponseEntity<NurseShiftSwap> requestShiftSwap(
            @RequestBody ShiftSwapRequest request,
            Principal principal) {
        if (request == null || !StringUtils.hasText(request.toNurseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select the nurse to swap shifts with");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(nursingService.requestShiftSwap(
                getAccount(principal).getId(), request.toNurseId(), request.wardId(),
                request.shift(), request.date(), request.note()));
    }

    @PostMapping("/shift-swaps/{swapId}/decision")
    public NurseShiftSwap decideShiftSwap(
            @PathVariable String swapId,
            @RequestBody ShiftSwapDecisionRequest request,
            Principal principal) {
        if (request == null || request.approve() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose whether to approve or decline the swap");
        }
        return nursingService.decideShiftSwap(
                swapId, request.approve(), getAccount(principal).getId(), request.note());
    }

    @GetMapping("/assignments")
    public List<PatientAssignment> getAssignments(Principal principal) {
        return nursingService.getAssignments(getAccount(principal).getId());
    }

    @GetMapping("/unassigned-patients")
    public List<Patient> getUnassignedPatients(Principal principal) {
        return nursingService.getUnassignedPatients(getAccount(principal).getId());
    }

    @GetMapping("/patients/assignments")
    public List<Map<String, Object>> getPatientAssignmentBoard(Principal principal) {
        return nursingService.getPatientAssignmentBoard(getAccount(principal).getId());
    }

    @GetMapping("/care-records")
    public List<NursingCareRecord> getCareRecords(Principal principal) {
        return nursingService.getCareRecords(getAccount(principal).getId());
    }

    @GetMapping("/dashboard")
    public List<Map<String, Object>> nurseDashboard(Principal principal) {
        return nursingService.getNurseDashboard(getAccount(principal).getId());
    }

    @PostMapping("/patients/{patientId}/assignments")
    public ResponseEntity<PatientAssignment> assignPatient(
            @PathVariable String patientId,
            @RequestBody AssignmentRequest request,
            Principal principal) {
        if (request == null || !StringUtils.hasText(request.nurseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a nurse to assign");
        }
        PatientAssignment assignment = nursingService.assignPatient(
                patientId, request.nurseId(), request.role(), request.shift(), principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @PostMapping("/patients/{patientId}/auto-assign")
    public ResponseEntity<PatientAssignment> autoAssignPatient(
            @PathVariable String patientId,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(nursingService.autoAssignPatient(patientId, principal.getName()));
    }

    @PutMapping("/patients/{patientId}/transfer")
    public Patient transferPatient(
            @PathVariable String patientId,
            @RequestBody AdmissionTransferRequest request,
            Principal principal) {
        if (request == null || !StringUtils.hasText(request.wardId()) || !StringUtils.hasText(request.bedId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select the destination ward and vacant bed");
        }
        return nursingService.transferPatient(
                patientId, request.wardId(), request.bedId(), principal.getName());
    }

    @PostMapping("/wards/{wardId}/assignments")
    public ResponseEntity<List<PatientAssignment>> assignByWard(
            @PathVariable String wardId,
            @RequestBody WardAssignmentRequest request,
            Principal principal) {
        if (request == null || !StringUtils.hasText(request.nurseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a nurse to assign");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(nursingService.assignByWard(
                wardId, request.bedFrom(), request.bedTo(), request.nurseId(),
                request.role(), request.shift(), principal.getName()));
    }

    @PutMapping("/patients/{patientId}/nurse")
    public ResponseEntity<Patient> assignPrimaryNurse(
            @PathVariable String patientId,
            @RequestBody NurseAssignmentRequest request,
            Principal principal) {
        if (request == null || !StringUtils.hasText(request.nurseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a nurse to assign");
        }
        nursingService.assignPatient(patientId, request.nurseId(), "PRIMARY", null, principal.getName());
        List<Patient> matches = patientRepository.findAllByPatientId(patientId);
        if (matches.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient ID is not unique");
        return ResponseEntity.ok(matches.get(0));
    }

    @PostMapping("/patients/{patientId}/care-records")
    public ResponseEntity<NursingCareRecord> addCareRecord(
            @PathVariable String patientId,
            @RequestBody NursingCareRecord record,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(nursingService.addCareRecord(getAccount(principal).getId(), patientId, record));
    }

    @PostMapping("/patients/{patientId}/care-records/{recordId}/complete")
    public NursingCareRecord completeCareRecord(
            @PathVariable String patientId,
            @PathVariable String recordId,
            @RequestBody(required = false) NursingCareRecord update,
            Principal principal) {
        return nursingService.completeCareRecord(getAccount(principal).getId(), patientId, recordId, update);
    }

    @GetMapping("/handovers")
    public List<NurseHandover> getPendingHandovers(Principal principal) {
        return nursingService.getPendingHandovers(getAccount(principal).getId());
    }

    @PostMapping("/patients/{patientId}/handovers")
    public ResponseEntity<NurseHandover> createHandover(
            @PathVariable String patientId,
            @RequestBody HandoverRequest request,
            Principal principal) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Handover details are required");
        return ResponseEntity.status(HttpStatus.CREATED).body(nursingService.createHandover(
                getAccount(principal).getId(), patientId, request.nurseId(), request.shift(), request.note()));
    }

    @PostMapping("/handovers/{handoverId}/acknowledge")
    public NurseHandover acknowledgeHandover(@PathVariable String handoverId, Principal principal) {
        return nursingService.acknowledgeHandover(getAccount(principal).getId(), handoverId);
    }

    private user getAccount(Principal principal) {
        List<user> accounts = userRepository.findAllByEmailIdIgnoreCase(principal.getName());
        if (accounts.size() != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nurse account could not be identified");
        }
        return accounts.get(0);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleNursingError(ResponseStatusException exception) {
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? "Nursing request could not be completed."
                : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }

    public record AssignmentRequest(String nurseId, String role, String shift) {}
    public record AdmissionTransferRequest(String wardId, String bedId) {}
    public record WardAssignmentRequest(String nurseId, String role, String shift, String bedFrom, String bedTo) {}
    public record BedStatusRequest(String status, String reason, String holdUntil) {}
    public record BedWaitingListRequest(
            String patientId, String wardId, String preferredAcType, String preferredCategory, String priority) {}
    public record ShiftSwapRequest(String toNurseId, String wardId, String shift, String date, String note) {}
    public record ShiftSwapDecisionRequest(Boolean approve, String note) {}
    public record NurseAssignmentRequest(String nurseId) {}
    public record HandoverRequest(String nurseId, String shift, String note) {}
}
