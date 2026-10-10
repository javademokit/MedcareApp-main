package com.example.MedcareApp.Entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Data
@Document(collection = "appointments")
public class Appointment {
    @Id
    private String id = UUID.randomUUID().toString();

    private String patientId;
    private String patientName;
    private String gender;
    private String patientAge;
    private String mobileNo;
    private String patientEmailId;
    private String patientAddress;
    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean registerNewPatient;

    private String doctorId;
    private String doctor;
    private String date;
    private String time;
    private String reason;
    private String fee;
    private String appointmentStatus = "pending";
    @Transient
    private String invoiceId;
    @Transient
    private String invoiceNumber;
    @Transient
    private String billingStatus;
    @Transient
    private String balanceDue;

    public String getAppointmentStatus() {
        return appointmentStatus;
    }

    public void setAppointmentStatus(String appointmentStatus) {
        this.appointmentStatus = appointmentStatus;
    }
}
