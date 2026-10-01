package com.example.MedcareApp.Entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Data
@Document(collection = "appointments")
public class Appointment {
    @Id
    private String id = UUID.randomUUID().toString();

    private String patientName;
    private String gender;
    private String patientAge;
    private String mobileNo;
    private String patientEmailId;
    private String patientAddress;

    private String doctor;
    private String date;
    private String time;
    private String reason;
    private String fee;
    private String appointmentStatus;

    public String getAppointmentStatus() {
        return appointmentStatus;
    }

    public void setAppointmentStatus(String appointmentStatus) {
        this.appointmentStatus = appointmentStatus;
    }
}
