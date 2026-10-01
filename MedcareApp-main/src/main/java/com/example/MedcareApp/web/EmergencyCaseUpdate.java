package com.example.MedcareApp.web;

import lombok.Data;

@Data
public class EmergencyCaseUpdate {
    private String severity;
    private String status;
    private String assignedClinician;
    private Boolean ambulanceRequired;
    private String ambulanceStatus;
    private String transferStatus;
    private String transferDestination;
    private String triageNotes;
}
