package com.example.MedcareApp.Entity;

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
    private String gender;
    private String patientEmailId;
    private String patientmobileNo;
    private String patientAddress;
    private String patientPrescription;
    private String patientAppointmentdate;
    private String patientAdmitdate;
    private String patientWardnum;
    private String patientDischargedate;
    private String patientNurseassign;
}
