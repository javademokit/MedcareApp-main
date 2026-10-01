package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.emergency.EmergencyCase;
import com.example.MedcareApp.Interafce.EmergencyCaseRepository;
import com.example.MedcareApp.web.EmergencyCaseUpdate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EmergencyService {
    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "HIGH", "MODERATE", "LOW");
    private static final Set<String> STATUSES = Set.of("WAITING_FOR_TRIAGE", "TRIAGED", "IN_CARE", "TRANSFERRED", "CLOSED");
    private static final Set<String> AMBULANCE_STATUSES = Set.of("NOT_REQUIRED", "REQUESTED", "DISPATCHED", "EN_ROUTE", "ARRIVED");
    private static final Set<String> TRANSFER_STATUSES = Set.of("NOT_REQUIRED", "REQUESTED", "IN_TRANSIT", "COMPLETED");

    private final EmergencyCaseRepository repository;

    public List<EmergencyCase> getCases() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public EmergencyCase createCase(EmergencyCase emergencyCase) {
        validateChoice(emergencyCase.getSeverity(), SEVERITIES, "severity");
        emergencyCase.setStatus("WAITING_FOR_TRIAGE");
        emergencyCase.setCriticalAlert("CRITICAL".equals(emergencyCase.getSeverity()));
        if (!emergencyCase.isAmbulanceRequired()) emergencyCase.setAmbulanceStatus("NOT_REQUIRED");
        else if (emergencyCase.getAmbulanceStatus() == null || "NOT_REQUIRED".equals(emergencyCase.getAmbulanceStatus())) {
            emergencyCase.setAmbulanceStatus("REQUESTED");
        }
        if (emergencyCase.getTransferStatus() == null) emergencyCase.setTransferStatus("NOT_REQUIRED");
        Instant now = Instant.now();
        emergencyCase.setCreatedAt(now);
        emergencyCase.setUpdatedAt(now);
        return repository.save(emergencyCase);
    }

    public EmergencyCase updateCase(String id, EmergencyCaseUpdate update) {
        EmergencyCase current = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergency case not found"));
        if (update.getSeverity() != null) {
            validateChoice(update.getSeverity(), SEVERITIES, "severity");
            current.setSeverity(update.getSeverity());
            current.setCriticalAlert("CRITICAL".equals(update.getSeverity()));
        }
        if (update.getStatus() != null) {
            validateChoice(update.getStatus(), STATUSES, "status");
            current.setStatus(update.getStatus());
            if ("TRANSFERRED".equals(update.getStatus()) && "NOT_REQUIRED".equals(current.getTransferStatus())) {
                current.setTransferStatus("IN_TRANSIT");
            }
        }
        if (update.getAssignedClinician() != null) current.setAssignedClinician(update.getAssignedClinician());
        if (update.getAmbulanceRequired() != null) {
            current.setAmbulanceRequired(update.getAmbulanceRequired());
            if (!update.getAmbulanceRequired()) current.setAmbulanceStatus("NOT_REQUIRED");
            else if ("NOT_REQUIRED".equals(current.getAmbulanceStatus())) current.setAmbulanceStatus("REQUESTED");
        }
        if (update.getAmbulanceStatus() != null) {
            validateChoice(update.getAmbulanceStatus(), AMBULANCE_STATUSES, "ambulance status");
            current.setAmbulanceStatus(update.getAmbulanceStatus());
        }
        if (update.getTransferStatus() != null) {
            validateChoice(update.getTransferStatus(), TRANSFER_STATUSES, "transfer status");
            current.setTransferStatus(update.getTransferStatus());
        }
        if (update.getTransferDestination() != null) current.setTransferDestination(update.getTransferDestination());
        if (update.getTriageNotes() != null) current.setTriageNotes(update.getTriageNotes());
        current.setUpdatedAt(Instant.now());
        return repository.save(current);
    }

    private void validateChoice(String value, Set<String> allowed, String field) {
        if (value == null || !allowed.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field);
        }
    }
}
