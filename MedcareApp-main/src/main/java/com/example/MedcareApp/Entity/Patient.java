package com.example.MedcareApp.Entity;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.*;

@Document(collection = "patients")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Patient {
    @Id
    private String id;

    private String patientId;
    private String patientName;
    private String patientAge;
    private LocalDate dateOfBirth;
    private String gender;
    private String patientEmailId;
    private String patientmobileNo;
    private String patientAddress;
    private String patientPostalCode;
    @JsonIgnore
    private String patientPanEncrypted;
    @JsonIgnore
    private String patientAadhaarEncrypted;
    @JsonIgnore
    private String patientPanLastFour;
    @JsonIgnore
    private String patientAadhaarLastFour;
    private String patientPrescription;
    private String patientAppointmentdate;
    private String patientAdmitdate;
    private String patientWardnum;
    private String patientWardId;
    private String patientBedId;
    private String patientDischargedate;
    private String patientNurseassign;
    private String patientNurseId;
    private String patientAllergies;

    @JsonProperty("patientPanMasked")
    public String getPatientPanMasked() {
        return maskIdentifier(patientPanLastFour);
    }

    @JsonProperty("patientAadhaarMasked")
    public String getPatientAadhaarMasked() {
        return maskIdentifier(patientAadhaarLastFour);
    }

    private String maskIdentifier(String lastFour) {
        return lastFour == null || lastFour.isBlank() ? "" : "•••• " + lastFour;
    }
}
